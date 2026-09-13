package wiki.asaf.wikisayit.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import wiki.asaf.wikisayit.data.local.db.RecordingEntryType
import wiki.asaf.wikisayit.data.local.db.SpeakerProfileWithLanguages
import wiki.asaf.wikisayit.data.local.settings.SettingsRepository
import wiki.asaf.wikisayit.data.profile.ProfileRepository
import wiki.asaf.wikisayit.data.stats.StatsRepository
import wiki.asaf.wikisayit.data.wikidata.WbEntityType
import wiki.asaf.wikisayit.data.wikidata.WbSearchResult
import wiki.asaf.wikisayit.data.wikidata.WikidataExistenceChecker
import wiki.asaf.wikisayit.data.wikidata.WikidataLabelMatcher
import wiki.asaf.wikisayit.data.wikidata.WikidataQueryListBuilder
import javax.inject.Inject
import kotlin.math.max

private const val READY_PHASE_MILLIS = 600L
private const val SPEAKING_PHASE_MILLIS = 1200L
private const val TICK_MILLIS = 100L
private const val REVIEW_PLAYING_MILLIS = 1200L
private const val REVIEW_DECISION_SECONDS = 1.5f
private const val UPLOAD_STEP_MILLIS = 500L

/**
 * Drives the whole recording flow (Profile through Done) from one activity-scoped ViewModel.
 * List building against a Wikipedia category (s-dbm.4) still isn't implemented, so [buildList]
 * simulates that one source without fabricating results it can't back up: a pasted list is
 * matched against live Wikidata via [WikidataLabelMatcher] (one hit auto-resolves, 2+ hits go
 * through [resolveDisambiguation]/[resolveDisambiguationRecordAll], zero hits are kept unresolved
 * rather than dropped); a SPARQL query is run for real via [WikidataQueryListBuilder]; a category
 * source still seeds a small placeholder queue pending s-dbm.4. [startCheck] is real: it runs the
 * built list through [WikidataExistenceChecker] against live Wikidata. The recording/review
 * timing loops are likewise simulated pending the audio engine (s-7lo) — [RecordingBlocker] and
 * the phase timings are the seams that work will plug into.
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecordingFlowUiState())
        val uiState: StateFlow<RecordingFlowUiState> = _uiState.asStateFlow()

        private var tickerJob: Job? = null

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
                ListSourceType.QUERY -> resolveQueryList(state)
                else -> {
                    val entries = placeholderQueue()
                    _uiState.update {
                        it.copy(
                            finalQueue = entries,
                            rawCount = entries.size,
                            checkDone = 0,
                            excludedCount = 0,
                            formsAddedCount = 0,
                            listBuildStage = ListBuildStage.FRESH,
                        )
                    }
                }
            }
        }

        private fun resolveQueryList(state: RecordingFlowUiState) {
            val query = state.sourceText
            val language = state.language?.isoCode.orEmpty()

            tickerJob?.cancel()
            _uiState.update {
                it.copy(listBuildStage = ListBuildStage.RESOLVING, rawCount = 0, resolveDone = 0)
            }
            tickerJob =
                viewModelScope.launch {
                    val entries = queryListBuilder.build(query, language)
                    _uiState.update {
                        it.copy(
                            finalQueue = entries,
                            rawCount = entries.size,
                            checkDone = 0,
                            excludedCount = 0,
                            formsAddedCount = 0,
                            listBuildStage = ListBuildStage.FRESH,
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
                    lines.forEachIndexed { index, line ->
                        val candidates = labelMatcher.search(line, entityType, language)
                        when {
                            candidates.isEmpty() -> resolved += unresolvedEntry(line, entryKind)
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
                        it.copy(finalQueue = resolved, disambiguationQueue = pending, disambiguationIndex = 0)
                    }
                    advanceListBuild()
                }
        }

        private fun unresolvedEntry(
            label: String,
            kind: EntryKind,
        ): QueueEntry = QueueEntry(label = label, kind = kind, detail = "no Wikidata match found")

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

        private fun placeholderQueue(): List<QueueEntry> =
            listOf(
                QueueEntry("water", EntryKind.FORM, "lexeme form · singular", lexemeId = "L3302", formId = "L3302-F1"),
                QueueEntry("Jerusalem", EntryKind.ITEM, "Wikidata item · city", qid = "Q1218"),
                QueueEntry(
                    "hedgehog",
                    EntryKind.FORM,
                    "lexeme form · singular",
                    lexemeId = "L45032",
                    formId = "L45032-F1",
                ),
            )

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

        private fun runRecordingLoop() {
            tickerJob?.cancel()
            tickerJob =
                viewModelScope.launch {
                    while (_uiState.value.currentRecordingEntry != null) {
                        _uiState.update { it.copy(recordingPhase = RecordingPhase.READY) }
                        delay(READY_PHASE_MILLIS)
                        _uiState.update { it.copy(recordingPhase = RecordingPhase.SPEAKING) }
                        delay(SPEAKING_PHASE_MILLIS)
                        var remaining = _uiState.value.settings.silenceThresholdSeconds
                        _uiState.update {
                            it.copy(recordingPhase = RecordingPhase.SILENCE, silenceRemainingSeconds = remaining)
                        }
                        while (remaining > 0f) {
                            delay(TICK_MILLIS)
                            remaining = max(0f, remaining - TICK_MILLIS / 1000f)
                            _uiState.update { it.copy(silenceRemainingSeconds = remaining) }
                        }
                        commitWord()
                    }
                }
        }

        private fun commitWord() {
            val state = _uiState.value
            val nextIndex = state.recordingIndex + 1
            if (nextIndex >= state.recordingQueue.size) {
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
            } else {
                _uiState.update { it.copy(recordingIndex = nextIndex) }
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
                _uiState.update {
                    it.copy(
                        listBuildStage = ListBuildStage.FRESH,
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
                        delay(REVIEW_PLAYING_MILLIS)
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
            val state = _uiState.value
            _uiState.value = RecordingFlowUiState(profiles = state.profiles, settings = state.settings)
        }

        // --- contribution ---

        fun contribute() {
            tickerJob?.cancel()
            _uiState.update { it.copy(uploadIndex = 0, uploadStepsDone = 0) }
            val profileId = _uiState.value.activeProfile?.profile?.id ?: return
            tickerJob =
                viewModelScope.launch {
                    val approved = _uiState.value.approved
                    for (index in approved.indices) {
                        _uiState.update { it.copy(uploadIndex = index, uploadStepsDone = 0) }
                        repeat(3) { step ->
                            delay(UPLOAD_STEP_MILLIS)
                            _uiState.update { it.copy(uploadStepsDone = step + 1) }
                        }
                        val entryType =
                            if (approved[index].kind == EntryKind.FORM) {
                                RecordingEntryType.LEXEME_FORM
                            } else {
                                RecordingEntryType.WIKIDATA_ITEM
                            }
                        statsRepository.recordContribution(profileId, entryType)
                    }
                    _uiState.update { it.copy(uploadIndex = approved.size, autoNavigateTo = FlowScreen.DONE) }
                }
        }
    }

private fun WbSearchResult.toDisambiguationCandidate(fallbackLabel: String) =
    DisambiguationCandidate(id = id, label = label ?: fallbackLabel, description = description)
