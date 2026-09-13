package wiki.asaf.wikisayit.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.audio.AudioPlayer
import wiki.asaf.wikisayit.audio.RecordingEngine
import wiki.asaf.wikisayit.audio.RecordingFileStore
import wiki.asaf.wikisayit.data.commons.CommonsUploader
import wiki.asaf.wikisayit.data.local.db.RecordingEntryType
import wiki.asaf.wikisayit.data.local.db.SpeakerProfileWithLanguages
import wiki.asaf.wikisayit.data.local.settings.SettingsRepository
import wiki.asaf.wikisayit.data.profile.ProfileRepository
import wiki.asaf.wikisayit.data.stats.StatsRepository
import wiki.asaf.wikisayit.data.wikidata.P443StatementWriter
import wiki.asaf.wikisayit.data.wikidata.WbEntityType
import wiki.asaf.wikisayit.data.wikidata.WbSearchResult
import wiki.asaf.wikisayit.data.wikidata.WikidataExistenceChecker
import wiki.asaf.wikisayit.data.wikidata.WikidataLabelMatcher
import wiki.asaf.wikisayit.data.wikidata.WikidataQueryListBuilder
import wiki.asaf.wikisayit.data.wikipedia.WikipediaCategorySource
import java.io.File
import javax.inject.Inject
import kotlin.math.max

private const val TICK_MILLIS = 100L
private const val REVIEW_DECISION_SECONDS = 1.5f

/**
 * Drives the whole recording flow (Profile through Done) from one activity-scoped ViewModel.
 * All three list sources are real: a pasted list is matched against live Wikidata via
 * [WikidataLabelMatcher] (one hit auto-resolves, 2+ hits go through
 * [resolveDisambiguation]/[resolveDisambiguationRecordAll], zero hits are kept unresolved rather
 * than dropped); a SPARQL query is run via [WikidataQueryListBuilder]; a Wikipedia category is
 * traversed via [WikipediaCategorySource]. [startCheck] is real too: it runs the built list
 * through [WikidataExistenceChecker] against live Wikidata. The recording loop ([runRecordingLoop])
 * is real as well, driven by [RecordingEngine]'s event stream (s-7lo/s-lfi.3); Skip/Redo/Stop
 * abandon an in-progress take by cancelling [tickerJob], which the engine's docs call out as the
 * supported way to release the mic mid-word. The review loop plays each take back for real via
 * [audioPlayer] before its Redo/Drop decision window opens.
 */
@HiltViewModel
class RecordingFlowViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val settingsRepository: SettingsRepository,
        private val statsRepository: StatsRepository,
        private val existenceChecker: WikidataExistenceChecker,
        private val labelMatcher: WikidataLabelMatcher,
        private val queryListBuilder: WikidataQueryListBuilder,
        private val categorySource: WikipediaCategorySource,
        private val recordingEngine: RecordingEngine,
        private val recordingFileStore: RecordingFileStore,
        private val audioPlayer: AudioPlayer,
        private val commonsUploader: CommonsUploader,
        private val p443StatementWriter: P443StatementWriter,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecordingFlowUiState())
        val uiState: StateFlow<RecordingFlowUiState> = _uiState.asStateFlow()

        private var tickerJob: Job? = null
        private var replayJob: Job? = null

        init {
            viewModelScope.launch {
                profileRepository.observeProfiles().collect { profiles ->
                    _uiState.update { it.copy(profiles = profiles) }
                }
            }
            viewModelScope.launch {
                settingsRepository.settings.collect { settings ->
                    _uiState.update { it.copy(settings = settings) }
                }
            }
        }

        // --- profile / language ---

        fun selectProfile(profile: SpeakerProfileWithLanguages) {
            _uiState.update { it.copy(activeProfile = profile) }
            viewModelScope.launch { settingsRepository.setLastUsedProfileId(profile.profile.id) }
        }

        fun selectLanguage(language: SelectedLanguage) {
            _uiState.update { it.copy(language = language) }
        }

        fun recordingCountFor(profileId: Long) = statsRepository.observeRecordingCountForProfile(profileId)

        // --- list sourcing ---

        fun selectSourceType(type: ListSourceType) {
            _uiState.update {
                it.copy(listSourceType = type, listBuildStage = ListBuildStage.SOURCE_FORM, sourceText = "")
            }
        }

        fun backToSourcePick() {
            _uiState.update { it.copy(listBuildStage = ListBuildStage.PICK_SOURCE) }
        }

        fun updateSourceText(text: String) {
            _uiState.update { it.copy(sourceText = text) }
        }

        fun updateMatchAs(matchAs: MatchAs) {
            _uiState.update { it.copy(matchAs = matchAs) }
        }

        fun updateCategoryDepth(depth: CategoryDepth) {
            _uiState.update { it.copy(categoryDepth = depth) }
        }

        fun buildList() {
            val state = _uiState.value
            when (state.listSourceType) {
                ListSourceType.PASTE -> resolvePastedList(state)
                ListSourceType.QUERY -> {
                    val language = state.language?.isoCode.orEmpty()
                    resolveAsyncList { queryListBuilder.build(state.sourceText, language) }
                }
                ListSourceType.CATEGORY -> {
                    val language = state.language?.isoCode.orEmpty()
                    resolveAsyncList { categorySource.build(state.sourceText, language, state.categoryDepth) }
                }
                null -> Unit
            }
        }

        /** Runs [build] (a SPARQL query execution or a category traversal) on a background job
         * while the RESOLVING stage shows an indeterminate spinner, then lands on FRESH — or, if
         * fetching failed and left nothing to show, jumps straight to EMPTY with
         * [RecordingFlowUiState.listBuildHadError] set so that screen can explain the list
         * couldn't be fetched rather than claiming it's genuinely empty (s-fns). */
        private fun resolveAsyncList(build: suspend () -> ListBuildResult) {
            tickerJob?.cancel()
            _uiState.update {
                it.copy(
                    listBuildStage = ListBuildStage.RESOLVING,
                    rawCount = 0,
                    resolveDone = 0,
                    listBuildHadError = false,
                )
            }
            tickerJob =
                viewModelScope.launch {
                    val result = build()
                    _uiState.update {
                        it.copy(
                            finalQueue = result.entries,
                            rawCount = result.entries.size,
                            checkDone = 0,
                            excludedCount = 0,
                            formsAddedCount = 0,
                            listBuildHadError = result.hadFetchError,
                            listBuildStage =
                                if (result.entries.isEmpty() && result.hadFetchError) {
                                    ListBuildStage.EMPTY
                                } else {
                                    ListBuildStage.FRESH
                                },
                        )
                    }
                }
        }

        private fun resolvePastedList(state: RecordingFlowUiState) {
            val lines = state.sourceText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val language = state.language?.isoCode.orEmpty()
            val entryKind = if (state.matchAs == MatchAs.ITEMS) EntryKind.ITEM else EntryKind.FORM
            val entityType = if (state.matchAs == MatchAs.ITEMS) WbEntityType.ITEM else WbEntityType.LEXEME

            tickerJob?.cancel()
            _uiState.update {
                it.copy(
                    listBuildStage = ListBuildStage.RESOLVING,
                    rawCount = lines.size,
                    resolveDone = 0,
                    disambiguationQueue = emptyList(),
                    disambiguationIndex = 0,
                )
            }
            tickerJob =
                viewModelScope.launch {
                    val resolved = mutableListOf<QueueEntry>()
                    val pending = mutableListOf<DisambiguationCase>()
                    var hadError = false
                    lines.forEachIndexed { index, line ->
                        val searchResult = labelMatcher.search(line, entityType, language)
                        val candidates = searchResult.candidates
                        if (searchResult.hadError) hadError = true
                        when {
                            candidates.isEmpty() ->
                                resolved += unresolvedEntry(line, entryKind, networkError = searchResult.hadError)
                            candidates.size == 1 ->
                                resolved += candidates[0].toDisambiguationCandidate(line).toQueueEntry(entryKind)
                            else ->
                                pending +=
                                    DisambiguationCase(
                                        originalLabel = line,
                                        kind = entryKind,
                                        candidates = candidates.map { it.toDisambiguationCandidate(line) },
                                    )
                        }
                        _uiState.update { it.copy(resolveDone = index + 1) }
                    }
                    _uiState.update {
                        it.copy(
                            finalQueue = resolved,
                            disambiguationQueue = pending,
                            disambiguationIndex = 0,
                            listBuildHadError = hadError,
                        )
                    }
                    advanceListBuild()
                }
        }

        private fun unresolvedEntry(
            label: String,
            kind: EntryKind,
            networkError: Boolean,
        ): QueueEntry =
            QueueEntry(
                label = label,
                kind = kind,
                detail = if (networkError) "couldn't check — try again" else "no Wikidata match found",
            )

        private fun advanceListBuild() {
            _uiState.update {
                if (it.disambiguationIndex < it.disambiguationQueue.size) {
                    it.copy(listBuildStage = ListBuildStage.DISAMBIGUATING)
                } else {
                    it.copy(
                        rawCount = it.finalQueue.size,
                        checkDone = 0,
                        excludedCount = 0,
                        formsAddedCount = 0,
                        listBuildStage = ListBuildStage.FRESH,
                    )
                }
            }
        }

        fun resolveDisambiguation(candidate: DisambiguationCandidate) {
            val case = _uiState.value.currentDisambiguation ?: return
            val entry = candidate.toQueueEntry(case.kind)
            _uiState.update {
                it.copy(finalQueue = it.finalQueue + entry, disambiguationIndex = it.disambiguationIndex + 1)
            }
            advanceListBuild()
        }

        fun resolveDisambiguationRecordAll() {
            val case = _uiState.value.currentDisambiguation ?: return
            val entries = case.candidates.map { it.toQueueEntry(case.kind) }
            _uiState.update {
                it.copy(finalQueue = it.finalQueue + entries, disambiguationIndex = it.disambiguationIndex + 1)
            }
            advanceListBuild()
        }

        fun backFromDisambiguation() {
            tickerJob?.cancel()
            _uiState.update {
                it.copy(
                    listBuildStage = ListBuildStage.SOURCE_FORM,
                    disambiguationQueue = emptyList(),
                    disambiguationIndex = 0,
                )
            }
        }

        fun startCheck() {
            _uiState.update { it.copy(listBuildStage = ListBuildStage.CHECKING, checkDone = 0) }
            tickerJob?.cancel()
            tickerJob =
                viewModelScope.launch {
                    val state = _uiState.value
                    val result =
                        existenceChecker.check(
                            candidates = state.finalQueue,
                            preferredLanguage = state.language?.isoCode.orEmpty(),
                        ) { done -> _uiState.update { it.copy(checkDone = done) } }
                    _uiState.update {
                        it.copy(
                            finalQueue = result.finalQueue,
                            excludedCount = result.excludedCount,
                            formsAddedCount = result.formsAddedCount,
                            checkDone = it.rawCount,
                        )
                    }
                    finishCheck()
                }
        }

        fun skipCheck() {
            tickerJob?.cancel()
            _uiState.update { it.copy(checkDone = it.rawCount) }
            finishCheck()
        }

        private fun finishCheck() {
            _uiState.update {
                val finalCount = it.rawCount - it.excludedCount + it.formsAddedCount
                it.copy(listBuildStage = if (finalCount <= 0) ListBuildStage.EMPTY else ListBuildStage.CHECKED)
            }
        }

        fun pickDifferentList() {
            _uiState.update {
                it.copy(
                    listBuildStage = ListBuildStage.PICK_SOURCE,
                    listSourceType = null,
                    sourceText = "",
                    finalQueue = emptyList(),
                    rawCount = 0,
                    listBuildHadError = false,
                    disambiguationQueue = emptyList(),
                    disambiguationIndex = 0,
                )
            }
        }

        fun recordAsSecondTakes() {
            _uiState.update { it.copy(listBuildStage = ListBuildStage.CHECKED) }
        }

        // --- recording session ---

        fun startSession() {
            tickerJob?.cancel()
            _uiState.update {
                it.copy(
                    recordingQueue = it.finalQueue,
                    recordingIndex = 0,
                    recordingPass = 1,
                    reviewSession = emptyList(),
                    redoQueue = emptyList(),
                    approved = emptyList(),
                )
            }
            runRecordingLoop()
        }

        private enum class RecordOutcome { FINISHED, TOO_SHORT, INTERRUPTED }

        private fun runRecordingLoop() {
            tickerJob?.cancel()
            _uiState.update { it.copy(recordingBlocker = null) }
            tickerJob =
                viewModelScope.launch {
                    while (_uiState.value.currentRecordingEntry != null) {
                        when (recordCurrentWord()) {
                            RecordOutcome.FINISHED -> if (commitWord()) return@launch
                            RecordOutcome.TOO_SHORT -> Unit
                            RecordOutcome.INTERRUPTED -> return@launch
                        }
                    }
                }
        }

        /** Records the current word via [recordingEngine], translating its event stream into
         * [RecordingPhase]/[RecordingFlowUiState.silenceRemainingSeconds] updates. Cancelling
         * [tickerJob] (Skip/Redo/Stop) abandons the take cleanly — the engine releases the mic
         * and emits nothing further, so there's no outcome to handle for that case here. */
        private suspend fun recordCurrentWord(): RecordOutcome {
            val outputFile = recordingFileStore.newRecordingFile()
            val silenceThresholdSeconds = _uiState.value.settings.silenceThresholdSeconds
            var finishedFile: File? = null
            try {
                recordingEngine.recordWord(outputFile, silenceThresholdSeconds).collect { event ->
                    when (event) {
                        RecordingEngine.Event.Listening ->
                            _uiState.update { it.copy(recordingPhase = RecordingPhase.READY) }
                        RecordingEngine.Event.Speaking ->
                            _uiState.update { it.copy(recordingPhase = RecordingPhase.SPEAKING) }
                        is RecordingEngine.Event.Silence ->
                            _uiState.update {
                                it.copy(
                                    recordingPhase = RecordingPhase.SILENCE,
                                    silenceRemainingSeconds = event.remainingSeconds,
                                )
                            }
                        is RecordingEngine.Event.Finished -> finishedFile = event.file
                        RecordingEngine.Event.TooShort -> Unit
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(recordingBlocker = RecordingBlocker.Interrupted) }
                return RecordOutcome.INTERRUPTED
            }
            val file = finishedFile ?: return RecordOutcome.TOO_SHORT
            attachRecordedFile(file)
            return RecordOutcome.FINISHED
        }

        private fun attachRecordedFile(file: File) {
            _uiState.update { state ->
                val index = state.recordingIndex
                val entry = state.recordingQueue.getOrNull(index) ?: return@update state
                val queue = state.recordingQueue.toMutableList().apply { this[index] = entry.copy(audioFile = file) }
                state.copy(recordingQueue = queue)
            }
        }

        /** @return true if this was the last word (the session moved on to review). */
        private fun commitWord(): Boolean {
            val state = _uiState.value
            val nextIndex = state.recordingIndex + 1
            return if (nextIndex >= state.recordingQueue.size) {
                tickerJob?.cancel()
                _uiState.update {
                    it.copy(
                        reviewSession = it.recordingQueue,
                        reviewIndex = 0,
                        redoQueue = emptyList(),
                        autoNavigateTo = FlowScreen.REVIEW,
                    )
                }
                runReviewLoop()
                true
            } else {
                _uiState.update { it.copy(recordingIndex = nextIndex) }
                false
            }
        }

        fun redoCurrentWord() {
            runRecordingLoop()
        }

        fun skipCurrentWord() {
            val state = _uiState.value
            val queue = state.recordingQueue.toMutableList()
            if (state.recordingIndex !in queue.indices) return
            queue.removeAt(state.recordingIndex)
            tickerJob?.cancel()
            if (queue.isEmpty()) {
                // finalQueue is already the checked/expanded list from before recording started —
                // land on CHECKED (accurate counts, "Start" to try again), not FRESH: FRESH's
                // "Check" button would otherwise re-run the existence check against these
                // already-resolved form entries, which used to multiply them out again (s-fsk).
                _uiState.update {
                    it.copy(
                        listBuildStage = ListBuildStage.CHECKED,
                        rawCount = it.finalQueue.size,
                        excludedCount = 0,
                        formsAddedCount = 0,
                        recordingQueue = emptyList(),
                        autoNavigateTo = FlowScreen.LIST_SOURCE,
                    )
                }
                return
            }
            if (state.recordingIndex >= queue.size) {
                _uiState.update {
                    it.copy(
                        recordingQueue = queue,
                        reviewSession = queue,
                        reviewIndex = 0,
                        redoQueue = emptyList(),
                        autoNavigateTo = FlowScreen.REVIEW,
                    )
                }
                runReviewLoop()
            } else {
                _uiState.update { it.copy(recordingQueue = queue) }
                runRecordingLoop()
            }
        }

        fun stopSession() {
            tickerJob?.cancel()
            val state = _uiState.value
            val done = state.recordingQueue.take(state.recordingIndex)
            if (done.isEmpty()) {
                _uiState.update {
                    it.copy(
                        reviewSession = emptyList(),
                        approved = emptyList(),
                        redoQueue = emptyList(),
                        autoNavigateTo = FlowScreen.SUMMARY,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        reviewSession = done,
                        reviewIndex = 0,
                        redoQueue = emptyList(),
                        autoNavigateTo = FlowScreen.REVIEW,
                    )
                }
                runReviewLoop()
            }
        }

        fun clearRecordingBlocker() {
            _uiState.update { it.copy(recordingBlocker = null) }
        }

        fun setMicPermissionDenied() {
            tickerJob?.cancel()
            _uiState.update { it.copy(recordingBlocker = RecordingBlocker.MicPermissionDenied) }
        }

        // --- review ---

        private fun runReviewLoop() {
            tickerJob?.cancel()
            tickerJob =
                viewModelScope.launch {
                    while (_uiState.value.currentReviewEntry != null) {
                        _uiState.update { it.copy(reviewPlaying = true) }
                        _uiState.value.currentReviewEntry?.audioFile?.let { audioPlayer.play(it) }
                        var remaining = REVIEW_DECISION_SECONDS
                        _uiState.update { it.copy(reviewPlaying = false, reviewDecisionRemainingSeconds = remaining) }
                        while (remaining > 0f) {
                            delay(TICK_MILLIS)
                            remaining = max(0f, remaining - TICK_MILLIS / 1000f)
                            _uiState.update { it.copy(reviewDecisionRemainingSeconds = remaining) }
                        }
                        resolveReview(approve = true)
                    }
                }
        }

        fun reviewRedo() = resolveReview(approve = false, redo = true)

        fun reviewDrop() = resolveReview(approve = false, redo = false)

        private fun resolveReview(
            approve: Boolean,
            redo: Boolean = false,
        ) {
            val state = _uiState.value
            val item = state.currentReviewEntry ?: return
            val approved = if (approve) state.approved + item else state.approved
            val redoQueue = if (redo) state.redoQueue + item else state.redoQueue
            val nextIndex = state.reviewIndex + 1
            if (nextIndex >= state.reviewSession.size) {
                tickerJob?.cancel()
                if (redoQueue.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            approved = approved,
                            redoQueue = emptyList(),
                            recordingQueue = redoQueue,
                            recordingIndex = 0,
                            recordingPass = it.recordingPass + 1,
                            autoNavigateTo = FlowScreen.RECORDING,
                        )
                    }
                    runRecordingLoop()
                } else {
                    _uiState.update {
                        it.copy(approved = approved, redoQueue = emptyList(), autoNavigateTo = FlowScreen.SUMMARY)
                    }
                }
            } else {
                _uiState.update { it.copy(approved = approved, redoQueue = redoQueue, reviewIndex = nextIndex) }
            }
        }

        /** True once every take from this pass has been approved or dropped and no redo pass is pending. */
        fun isReviewFinished(): Boolean {
            val state = _uiState.value
            return state.reviewSession.isNotEmpty() && state.reviewIndex >= state.reviewSession.size
        }

        /** Replays one recording on demand, independent of the auto-advancing review loop — e.g.
         * from a play icon next to a row in the approved list. */
        fun replayEntry(entry: QueueEntry) {
            val file = entry.audioFile ?: return
            replayJob?.cancel()
            _uiState.update { it.copy(replayingEntryId = entry.evidenceId) }
            replayJob =
                viewModelScope.launch {
                    try {
                        audioPlayer.play(file)
                    } finally {
                        _uiState.update {
                            if (it.replayingEntryId == entry.evidenceId) it.copy(replayingEntryId = null) else it
                        }
                    }
                }
        }

        // --- summary / abandon ---

        fun askAbandon() {
            _uiState.update { it.copy(showAbandonDialog = true) }
        }

        fun cancelAbandon() {
            _uiState.update { it.copy(showAbandonDialog = false) }
        }

        fun doAbandon() = resetFlow()

        fun startOver() = resetFlow()

        private fun resetFlow() {
            tickerJob?.cancel()
            replayJob?.cancel()
            val state = _uiState.value
            _uiState.value = RecordingFlowUiState(profiles = state.profiles, settings = state.settings)
        }

        // --- contribution (2e: real Commons upload + P443 write, with per-entry failure states) ---

        fun contribute() {
            tickerJob?.cancel()
            val approved = _uiState.value.approved
            _uiState.update {
                it.copy(
                    uploadStates = approved.map { UploadEntryState() },
                    activeUploadIndex = null,
                    connectivityLost = false,
                )
            }
            tickerJob = viewModelScope.launch { runContributionLoop(approved.indices.toList()) }
        }

        /** Retries every row currently in the "needs attention" list, including name-conflict
         * rows the user hasn't renamed — those will simply fail again with the same tag. */
        fun retryFailedUploads() {
            tickerJob?.cancel()
            val failedIndices = _uiState.value.uploadNeedsAttention
            _uiState.update { state -> state.copy(uploadStates = state.uploadStates.map { it.clearFailure() }) }
            tickerJob = viewModelScope.launch { runContributionLoop(failedIndices) }
        }

        /** Stops retrying for now; whatever already succeeded stays contributed and the rest is
         * left on-device, per the design's "recordings stay on device until both steps succeed". */
        fun leaveFailuresForLater() {
            tickerJob?.cancel()
            _uiState.update { it.copy(activeUploadIndex = null, autoNavigateTo = FlowScreen.DONE) }
        }

        /** Appends a fresh numeric suffix to the entry's label so the next upload attempt gets a
         * new Commons filename, then retries just that one entry. */
        fun renameAndRetryFailedEntry(index: Int) {
            tickerJob?.cancel()
            _uiState.update { state ->
                val states = state.uploadStates.toMutableList()
                val current = states.getOrNull(index) ?: return@update state
                states[index] = current.clearFailure().copy(renameSuffix = current.renameSuffix + 1)
                state.copy(uploadStates = states)
            }
            tickerJob = viewModelScope.launch { runContributionLoop(listOf(index)) }
        }

        /** Drops a name-conflicted entry from the contribution altogether; its recording stays
         * on-device but is no longer part of this session's approved list. */
        fun discardFailedEntry(index: Int) {
            _uiState.update { state ->
                if (index !in state.approved.indices) return@update state
                state.copy(
                    approved = state.approved.toMutableList().apply { removeAt(index) },
                    uploadStates = state.uploadStates.toMutableList().apply { removeAt(index) },
                )
            }
            finishContributionIfComplete()
        }

        private fun UploadEntryState.clearFailure() = copy(failedStep = null, failureStatus = null, errorMessage = null)

        private suspend fun runContributionLoop(indices: List<Int>) {
            val profileId = _uiState.value.activeProfile?.profile?.id ?: return
            val isoCode = _uiState.value.language?.isoCode.orEmpty()
            val username = _uiState.value.username
            val speakerName = _uiState.value.speakerName
            for (index in indices) {
                val entry = _uiState.value.approved.getOrNull(index) ?: continue
                _uiState.update { it.copy(activeUploadIndex = index) }
                uploadEntry(index, entry, isoCode, username, speakerName, profileId)
            }
            _uiState.update { it.copy(activeUploadIndex = null) }
            finishContributionIfComplete()
        }

        private fun finishContributionIfComplete() {
            val state = _uiState.value
            if (state.approved.isNotEmpty() && state.uploadStates.all { it.isComplete }) {
                _uiState.update { it.copy(autoNavigateTo = FlowScreen.DONE) }
            }
        }

        private suspend fun uploadEntry(
            index: Int,
            entry: QueueEntry,
            isoCode: String,
            username: String,
            speakerName: String,
            profileId: Long,
        ) {
            val entryState = _uiState.value.uploadStates.getOrNull(index) ?: return
            val renamedEntry =
                if (entryState.renameSuffix > 0) {
                    entry.copy(
                        label = "${entry.label} (${entryState.renameSuffix + 1})",
                    )
                } else {
                    entry
                }
            val filename: String
            if (!entryState.commonsDone) {
                filename =
                    try {
                        commonsUploader.upload(renamedEntry, isoCode, username, speakerName)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        recordUploadFailure(index, UploadStepFailure.COMMONS, error)
                        return
                    }
                updateUploadState(index) { it.copy(commonsDone = true, categoriesDone = true) }
                _uiState.update { it.copy(connectivityLost = false) }
            } else {
                filename = renamedEntry.commonsFilename(isoCode, username, speakerName)
            }
            try {
                p443StatementWriter.addPronunciation(entry, filename)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                recordUploadFailure(index, UploadStepFailure.P443, error)
                return
            }
            updateUploadState(index) { it.copy(p443Done = true) }
            _uiState.update { it.copy(connectivityLost = false) }
            val entryType =
                if (entry.kind == EntryKind.FORM) RecordingEntryType.LEXEME_FORM else RecordingEntryType.WIKIDATA_ITEM
            statsRepository.recordContribution(profileId, entryType)
        }

        private fun recordUploadFailure(
            index: Int,
            step: UploadStepFailure,
            error: Throwable,
        ) {
            val (status, message) = classifyUploadFailure(error)
            updateUploadState(index) { it.copy(failedStep = step, failureStatus = status, errorMessage = message) }
            if (status == UploadFailureStatus.WAITING) {
                _uiState.update { it.copy(connectivityLost = true) }
            }
        }

        private fun updateUploadState(
            index: Int,
            transform: (UploadEntryState) -> UploadEntryState,
        ) {
            _uiState.update { state ->
                val states = state.uploadStates.toMutableList()
                val current = states.getOrNull(index) ?: return@update state
                states[index] = transform(current)
                state.copy(uploadStates = states)
            }
        }
    }

private fun WbSearchResult.toDisambiguationCandidate(fallbackLabel: String) =
    DisambiguationCandidate(id = id, label = label ?: fallbackLabel, description = description)
