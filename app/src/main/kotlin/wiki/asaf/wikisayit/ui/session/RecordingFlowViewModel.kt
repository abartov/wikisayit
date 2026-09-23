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
import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.data.local.db.RecordingEntryType
import wiki.asaf.wikisayit.data.local.db.SpeakerProfileWithLanguages
import wiki.asaf.wikisayit.data.local.settings.SettingsRepository
import wiki.asaf.wikisayit.data.profile.ProfileRepository
import wiki.asaf.wikisayit.data.skipped.SkippedEntryRepository
import wiki.asaf.wikisayit.data.stats.StatsRepository
import wiki.asaf.wikisayit.data.uploads.PendingUploadItem
import wiki.asaf.wikisayit.data.uploads.PendingUploadRepository
import wiki.asaf.wikisayit.data.uploads.PendingUploadResumer
import wiki.asaf.wikisayit.data.wikidata.CannedSparqlQuery
import wiki.asaf.wikisayit.data.wikidata.P443StatementWriter
import wiki.asaf.wikisayit.data.wikidata.WbEntityType
import wiki.asaf.wikisayit.data.wikidata.WbSearchResult
import wiki.asaf.wikisayit.data.wikidata.WikidataExistenceChecker
import wiki.asaf.wikisayit.data.wikidata.WikidataLabelMatcher
import wiki.asaf.wikisayit.data.wikidata.WikidataQueryListBuilder
import wiki.asaf.wikisayit.data.wikidata.buildQuery
import wiki.asaf.wikisayit.data.wikipedia.WikipediaCategorySource
import java.io.File
import javax.inject.Inject
import kotlin.math.max

private const val TICK_MILLIS = 100L
private const val REVIEW_DECISION_SECONDS = 1.5f

/** "Ready" and "set" each hold for this long, and the mic starts listening the moment "go"
 * appears; "go" then lingers this many milliseconds longer before the pre-roll clears (s-yns). */
private const val READY_SET_GO_STEP_MILLIS = 900L
private const val READY_SET_GO_LINGER_MILLIS = 200L

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
        private val skippedEntryRepository: SkippedEntryRepository,
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
        private val pendingUploadRepository: PendingUploadRepository,
        private val pendingUploadResumer: PendingUploadResumer,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RecordingFlowUiState())
        val uiState: StateFlow<RecordingFlowUiState> = _uiState.asStateFlow()

        private var tickerJob: Job? = null
        private var replayJob: Job? = null
        private var readySetGoJob: Job? = null
        private var manualStopSignal: MutableStateFlow<Boolean>? = null

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
            viewModelScope.launch { pendingUploadResumer.resumeAll() }
        }

        /** Screenshot-only escape hatch (see [wiki.asaf.wikisayit.ui.debug.ScreenshotFixtures]):
         * jumps straight to a fixture UI state instead of driving the real flow through it.
         * Gated on [BuildConfig.DEBUG], a compile-time constant, so release builds keep the
         * check but R8 dead-code-strips the call sites that would ever invoke it. */
        fun debugApplyState(state: RecordingFlowUiState) {
            if (wiki.asaf.wikisayit.BuildConfig.DEBUG) {
                _uiState.value = state
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

        fun askDeleteProfile(profileId: Long) {
            _uiState.update { it.copy(pendingDeleteProfileId = profileId) }
        }

        fun cancelDeleteProfile() {
            _uiState.update { it.copy(pendingDeleteProfileId = null) }
        }

        fun confirmDeleteProfile() {
            val profile = _uiState.value.profiles.firstOrNull { it.profile.id == _uiState.value.pendingDeleteProfileId }
            _uiState.update { it.copy(pendingDeleteProfileId = null) }
            if (profile != null) {
                viewModelScope.launch { profileRepository.deleteProfile(profile.profile) }
            }
        }

        // --- list sourcing ---

        fun selectSourceType(type: ListSourceType) {
            _uiState.update {
                it.copy(listSourceType = type, listBuildStage = ListBuildStage.SOURCE_FORM, sourceText = "")
            }
        }

        fun backToSourcePick() {
            _uiState.update { it.copy(listBuildStage = ListBuildStage.PICK_SOURCE) }
        }

        /** From the built list (step 4) back to the source form (step 3) — keeps the chosen
         * source type and its entered text/settings so the user doesn't have to redo them. */
        fun backToSourceForm() {
            _uiState.update { it.copy(listBuildStage = ListBuildStage.SOURCE_FORM) }
        }

        fun updateSourceText(text: String) {
            _uiState.update { it.copy(sourceText = text) }
        }

        fun updateMatchAs(matchAs: MatchAs) {
            _uiState.update { it.copy(matchAs = matchAs) }
        }

        /** Fills the query text field with one of the prepackaged queries (s-xxb), scoped to the
         * language already picked for this session — still editable before "Build the list". */
        fun applyCannedQuery(query: CannedSparqlQuery) {
            val isoCode = _uiState.value.language?.isoCode.orEmpty()
            _uiState.update { it.copy(sourceText = query.buildQuery(isoCode)) }
        }

        fun updateCategoryDepth(depth: CategoryDepth) {
            _uiState.update { it.copy(categoryDepth = depth) }
        }

        fun updateIncludeRecordedItems(include: Boolean) {
            _uiState.update { it.copy(includeRecordedItems = include) }
        }

        fun buildList() {
            val state = _uiState.value
            when (state.listSourceType) {
                ListSourceType.PASTE -> resolvePastedList(state)
                ListSourceType.QUERY -> buildQueryList(state)
                ListSourceType.CATEGORY -> buildCategoryList(state)
                null -> Unit
            }
        }

        /**
         * A category build (s-fi0) applies [GapsOnlyFilter] while it walks the category rather
         * than afterwards; the source draws on a pool larger than the list needs, so the filtered
         * list still reaches the configured size instead of shrinking by however much was dropped.
         *
         * Having filtered on P443 already, the pre-filtered list lands straight on the
         * ready-to-record screen: offering "check for existing recordings" there would just
         * re-run the check that produced this list.
         */
        private fun buildCategoryList(state: RecordingFlowUiState) {
            val language = state.language?.isoCode.orEmpty()
            val filter = GapsOnlyFilter(language, excludeRecorded = !state.includeRecordedItems)
            viewModelScope.launch { settingsRepository.rememberCategory(language, state.sourceText) }
            resolveAsyncList(landOnChecked = filter.excludeRecorded, extraUpdate = filter::applyTo) {
                categorySource.build(
                    categoryName = state.sourceText,
                    languageCode = language,
                    depth = state.categoryDepth,
                    maxListSize = state.settings.maxListSize,
                    filter = filter::apply,
                )
            }
        }

        /**
         * A SPARQL query build filters exactly as a category build does (s-3ux): the query service
         * gets a `LIMIT` raised above the configured list size, and the surplus hits cover whatever
         * the skip-list and the P443 check drop, so the list still reaches its full length instead
         * of handing back the same skipped words every time.
         */
        private fun buildQueryList(state: RecordingFlowUiState) {
            val language = state.language?.isoCode.orEmpty()
            val filter = GapsOnlyFilter(language, excludeRecorded = !state.includeRecordedItems)
            resolveAsyncList(landOnChecked = filter.excludeRecorded, extraUpdate = filter::applyTo) {
                queryListBuilder.build(
                    query = state.sourceText,
                    preferredLanguage = language,
                    maxListSize = state.settings.maxListSize,
                    filter = filter::apply,
                )
            }
        }

        /**
         * The "gaps only" filter shared by the two list sources that over-fetch and filter as they
         * go (s-fi0, s-3ux): entries skipped in an earlier session are always dropped, and — unless
         * "Include items that have pronunciations?" is ticked — so is anything that already carries
         * P443. Keeps running totals so the built list can report what it left out.
         */
        private inner class GapsOnlyFilter(
            private val language: String,
            val excludeRecorded: Boolean,
        ) {
            private var alreadyRecorded = 0
            private var previouslySkipped = 0

            /** Read once per build, on first use rather than up front, because building the filter
             * itself isn't a suspending context. */
            private var skippedIds: Set<String>? = null

            suspend fun apply(candidates: List<QueueEntry>): List<QueueEntry> {
                val skipped = skippedIds ?: skippedEntryRepository.skippedIds().also { skippedIds = it }
                val unskipped = candidates.filterNot { it.evidenceId in skipped }
                previouslySkipped += candidates.size - unskipped.size
                if (!excludeRecorded || unskipped.isEmpty()) return unskipped
                val checked = existenceChecker.check(unskipped, language)
                alreadyRecorded += checked.excludedCount
                return checked.finalQueue
            }

            /** Folds the totals into the state update that lands the finished list. */
            fun applyTo(state: RecordingFlowUiState): RecordingFlowUiState =
                state.copy(
                    listPreFiltered = excludeRecorded,
                    filteredAlreadyRecordedCount = alreadyRecorded,
                    filteredPreviouslySkippedCount = previouslySkipped,
                )
        }

        /** Runs [build] (a SPARQL query execution or a category traversal) on a background job
         * while the RESOLVING stage shows an indeterminate spinner, then lands on FRESH — or, if
         * fetching failed and left nothing to show, jumps straight to EMPTY with
         * [RecordingFlowUiState.listBuildHadError] set so that screen can explain the list
         * couldn't be fetched rather than claiming it's genuinely empty (s-fns).
         *
         * @param landOnChecked for builds that already dropped everything an existence check
         *   would have (see [buildCategoryList]): land on CHECKED, and treat an empty result as
         *   the EMPTY outcome — nothing in this category still needs a voice — rather than as a
         *   list waiting to be checked.
         * @param extraUpdate folded into the same state update that lands the result, for fields
         *   only the calling build knows about.
         */
        private fun resolveAsyncList(
            landOnChecked: Boolean = false,
            extraUpdate: (RecordingFlowUiState) -> RecordingFlowUiState = { it },
            build: suspend () -> ListBuildResult,
        ) {
            tickerJob?.cancel()
            _uiState.update {
                it.copy(
                    listBuildStage = ListBuildStage.RESOLVING,
                    rawCount = 0,
                    resolveDone = 0,
                    excludedEntries = emptyList(),
                    secondTakes = false,
                    listBuildHadError = false,
                    listPreFiltered = false,
                    filteredAlreadyRecordedCount = 0,
                    filteredPreviouslySkippedCount = 0,
                )
            }
            tickerJob =
                viewModelScope.launch {
                    val result = build()
                    _uiState.update {
                        extraUpdate(
                            it.copy(
                                finalQueue = result.entries,
                                rawCount = result.entries.size,
                                checkDone = if (landOnChecked) result.entries.size else 0,
                                excludedCount = 0,
                                formsAddedCount = 0,
                                listBuildHadError = result.hadFetchError,
                                listBuildStage =
                                    when {
                                        result.entries.isEmpty() && (result.hadFetchError || landOnChecked) ->
                                            ListBuildStage.EMPTY
                                        landOnChecked -> ListBuildStage.CHECKED
                                        else -> ListBuildStage.FRESH
                                    },
                            ),
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
                    excludedEntries = emptyList(),
                    secondTakes = false,
                    listPreFiltered = false,
                    filteredAlreadyRecordedCount = 0,
                    filteredPreviouslySkippedCount = 0,
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
                            excludedEntries = result.excludedEntries,
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
            // No check ran, so nothing was held back as a second-take candidate.
            _uiState.update { it.copy(checkDone = it.rawCount, excludedEntries = emptyList()) }
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
                    excludedEntries = emptyList(),
                    secondTakes = false,
                    rawCount = 0,
                    listBuildHadError = false,
                    listPreFiltered = false,
                    filteredAlreadyRecordedCount = 0,
                    filteredPreviouslySkippedCount = 0,
                    disambiguationQueue = emptyList(),
                    disambiguationIndex = 0,
                )
            }
        }

        /**
         * Takes up the EMPTY outcome's "record them anyway, as second takes" offer: the entries the
         * existence check excluded become the queue (s-39z). It used to move on to the CHECKED
         * screen without touching the queue the check had just emptied, so the session started with
         * no words at all.
         */
        fun recordAsSecondTakes() {
            _uiState.update {
                if (it.excludedEntries.isEmpty()) {
                    it
                } else {
                    it.copy(
                        listBuildStage = ListBuildStage.CHECKED,
                        finalQueue = it.excludedEntries,
                        secondTakes = true,
                        // These all already have audio: there is no excluded/added breakdown left
                        // to report, just the count now queued up for a second take.
                        rawCount = it.excludedEntries.size,
                        checkDone = it.excludedEntries.size,
                        excludedCount = 0,
                        formsAddedCount = 0,
                    )
                }
            }
        }

        // --- recording session ---

        fun startSession() {
            tickerJob?.cancel()
            readySetGoJob?.cancel()
            _uiState.update {
                it.copy(
                    recordingQueue = it.finalQueue,
                    recordingIndex = 0,
                    recordingPass = 1,
                    reviewSession = emptyList(),
                    redoQueue = emptyList(),
                    approved = emptyList(),
                    readySetGoPhase = ReadySetGoPhase.READY,
                )
            }
            // Runs on its own job, distinct from [tickerJob], so that [beginRecordingForCurrentEntry]
            // (called mid-sequence, once "go" appears) can freely cancel/reassign tickerJob without
            // cancelling the coroutine it's called from.
            readySetGoJob =
                viewModelScope.launch {
                    delay(READY_SET_GO_STEP_MILLIS)
                    _uiState.update { it.copy(readySetGoPhase = ReadySetGoPhase.SET) }
                    delay(READY_SET_GO_STEP_MILLIS)
                    _uiState.update { it.copy(readySetGoPhase = ReadySetGoPhase.GO) }
                    beginRecordingForCurrentEntry()
                    delay(READY_SET_GO_LINGER_MILLIS)
                    _uiState.update {
                        if (it.readySetGoPhase == ReadySetGoPhase.GO) it.copy(readySetGoPhase = null) else it
                    }
                }
        }

        /** Starts recording the current queue entry the way [RecordingFlowUiState.manualMode]
         * calls for: the automatic loop in manual mode, or just resetting to an idle/READY state
         * so the user can press "Record" themselves. Used wherever the automatic path used to
         * unconditionally call [runRecordingLoop] (s-3fe). */
        private fun beginRecordingForCurrentEntry() {
            if (_uiState.value.manualMode) {
                tickerJob?.cancel()
                manualStopSignal = null
                _uiState.update {
                    it.copy(
                        recordingPhase = RecordingPhase.READY,
                        manualRecordingActive = false,
                        silenceRemainingSeconds = 0f,
                        autoDetectionDifficulty = false,
                    )
                }
            } else {
                runRecordingLoop()
            }
        }

        /** Toggles between the automatic recording loop and manual record/stop/next control
         * (s-3fe), useful in noisy environments where speech-onset/silence auto-detection isn't
         * reliable. Switching modes abandons any take in progress, same as Skip/Redo. */
        fun setManualMode(enabled: Boolean) {
            if (_uiState.value.manualMode == enabled) return
            _uiState.update { it.copy(manualMode = enabled) }
            if (_uiState.value.currentRecordingEntry != null) beginRecordingForCurrentEntry()
        }

        /** Starts a manual-mode take for the current queue entry; recording continues until
         * [stopManualRecording] is called, with no speech-silence auto-stop. */
        fun startManualRecording() {
            val state = _uiState.value
            if (!state.manualMode || state.currentRecordingEntry == null || state.manualRecordingActive) return
            tickerJob?.cancel()
            val stopSignal = MutableStateFlow(false)
            manualStopSignal = stopSignal
            _uiState.update { it.copy(manualRecordingActive = true) }
            tickerJob =
                viewModelScope.launch {
                    recordCurrentWordManual(stopSignal)
                    manualStopSignal = null
                    _uiState.update { it.copy(manualRecordingActive = false) }
                }
        }

        /** Ends the in-progress manual take (crops/pads/encodes what was captured so far); does
         * not advance the queue — that's [manualNext]'s job. */
        fun stopManualRecording() {
            manualStopSignal?.value = true
        }

        /** Advances to the next queue entry (or on to review, for the last one) once the current
         * entry has a take attached — the manual-mode analogue of what the automatic loop does on
         * every [RecordOutcome.FINISHED]. */
        fun manualNext() {
            if (_uiState.value.manualRecordingActive) return
            if (commitWord()) return
            _uiState.update { it.copy(recordingPhase = RecordingPhase.READY) }
        }

        private suspend fun recordCurrentWordManual(stopSignal: StateFlow<Boolean>) {
            val outputFile = recordingFileStore.newRecordingFile()
            var finished: RecordingEngine.Event.Finished? = null
            try {
                recordingEngine.recordWordManual(outputFile, stopSignal).collect { event ->
                    when (event) {
                        RecordingEngine.Event.Listening ->
                            _uiState.update { it.copy(recordingPhase = RecordingPhase.READY) }
                        RecordingEngine.Event.Speaking ->
                            _uiState.update { it.copy(recordingPhase = RecordingPhase.SPEAKING) }
                        is RecordingEngine.Event.Silence -> Unit
                        RecordingEngine.Event.DifficultyDetecting -> Unit
                        is RecordingEngine.Event.Finished -> finished = event
                        RecordingEngine.Event.TooShort -> Unit
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(recordingBlocker = RecordingBlocker.Interrupted) }
                return
            }
            val event = finished ?: return
            attachRecordedFile(event.file, event.durationSeconds)
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
            var finished: RecordingEngine.Event.Finished? = null
            try {
                recordingEngine.recordWord(outputFile, silenceThresholdSeconds).collect { event ->
                    when (event) {
                        RecordingEngine.Event.Listening ->
                            _uiState.update {
                                it.copy(recordingPhase = RecordingPhase.READY, autoDetectionDifficulty = false)
                            }
                        RecordingEngine.Event.Speaking ->
                            _uiState.update { it.copy(recordingPhase = RecordingPhase.SPEAKING) }
                        is RecordingEngine.Event.Silence ->
                            _uiState.update {
                                it.copy(
                                    recordingPhase = RecordingPhase.SILENCE,
                                    silenceRemainingSeconds = event.remainingSeconds,
                                    autoDetectionDifficulty = false,
                                )
                            }
                        RecordingEngine.Event.DifficultyDetecting ->
                            _uiState.update { it.copy(autoDetectionDifficulty = true) }
                        is RecordingEngine.Event.Finished -> finished = event
                        RecordingEngine.Event.TooShort -> Unit
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update { it.copy(recordingBlocker = RecordingBlocker.Interrupted) }
                return RecordOutcome.INTERRUPTED
            }
            val event = finished ?: return RecordOutcome.TOO_SHORT
            attachRecordedFile(event.file, event.durationSeconds)
            return RecordOutcome.FINISHED
        }

        private fun attachRecordedFile(
            file: File,
            durationSeconds: Float,
        ) {
            _uiState.update { state ->
                val index = state.recordingIndex
                val entry = state.recordingQueue.getOrNull(index) ?: return@update state
                val queue =
                    state.recordingQueue.toMutableList().apply {
                        this[index] = entry.copy(audioFile = file, durationSeconds = durationSeconds)
                    }
                state.copy(recordingQueue = queue)
            }
        }

        /** @return true if this was the last word (the session moved on to review, or — for a
         * single-word rerecord — replaced the approved entry and returned to the summary). */
        private fun commitWord(): Boolean {
            val state = _uiState.value
            val nextIndex = state.recordingIndex + 1
            if (nextIndex >= state.recordingQueue.size && state.rerecordIndex != null) {
                tickerJob?.cancel()
                val newTake = state.recordingQueue.getOrNull(state.recordingIndex)
                _uiState.update {
                    val approved =
                        if (newTake != null && state.rerecordIndex in it.approved.indices) {
                            it.approved.toMutableList().apply { this[state.rerecordIndex] = newTake }
                        } else {
                            it.approved
                        }
                    it.copy(
                        approved = approved,
                        recordingQueue = emptyList(),
                        rerecordIndex = null,
                        autoNavigateTo = FlowScreen.SUMMARY_RETURN,
                    )
                }
                return true
            }
            return if (nextIndex >= state.recordingQueue.size) {
                tickerJob?.cancel()
                _uiState.update {
                    it.copy(
                        reviewSession = it.recordingQueue,
                        reviewIndex = 0,
                        redoQueue = emptyList(),
                        reviewPassApprovedBaseline = it.approved.size,
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

        /** Re-records one already-approved entry from the summary screen (s-1a7's "undo" action
         * next to the play button): starts a fresh single-word recording, and — once it finishes
         * — replaces this entry's slot in [RecordingFlowUiState.approved] via [commitWord]'s
         * [RecordingFlowUiState.rerecordIndex] handling, without disturbing the rest of the list. */
        fun rerecordApprovedEntry(entry: QueueEntry) {
            val index = _uiState.value.approved.indexOfFirst { it.evidenceId == entry.evidenceId }
            if (index == -1) return
            tickerJob?.cancel()
            _uiState.update {
                it.copy(
                    recordingQueue = listOf(entry),
                    recordingIndex = 0,
                    rerecordIndex = index,
                    autoNavigateTo = FlowScreen.RECORDING,
                )
            }
            beginRecordingForCurrentEntry()
        }

        fun redoCurrentWord() {
            runRecordingLoop()
        }

        fun skipCurrentWord() {
            val state = _uiState.value
            val queue = state.recordingQueue.toMutableList()
            if (state.recordingIndex !in queue.indices) return
            val skipped = queue.removeAt(state.recordingIndex)
            tickerJob?.cancel()
            readySetGoJob?.cancel()
            if (state.rerecordIndex != null) {
                // Skipping a rerecord-in-progress cancels it: the previously approved take is
                // left untouched, so just return to the summary rather than the empty-queue
                // LIST_SOURCE path below (which is only correct for a from-scratch session).
                _uiState.update {
                    it.copy(
                        recordingQueue = emptyList(),
                        rerecordIndex = null,
                        autoNavigateTo = FlowScreen.SUMMARY_RETURN,
                    )
                }
                return
            }
            // A genuine skip (not a cancelled rerecord): remember it so future category builds
            // leave this entry out instead of offering it again every time (s-fi0.2).
            viewModelScope.launch { skippedEntryRepository.remember(skipped) }
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
                        reviewPassApprovedBaseline = it.approved.size,
                        autoNavigateTo = FlowScreen.REVIEW,
                    )
                }
                runReviewLoop()
            } else {
                _uiState.update { it.copy(recordingQueue = queue) }
                beginRecordingForCurrentEntry()
            }
        }

        fun stopSession() {
            tickerJob?.cancel()
            readySetGoJob?.cancel()
            val state = _uiState.value
            if (state.rerecordIndex != null) {
                // Stopping mid-rerecord cancels it, same as skipCurrentWord: nothing was
                // finished, so the previously approved take is left in place.
                _uiState.update {
                    it.copy(
                        recordingQueue = emptyList(),
                        rerecordIndex = null,
                        autoNavigateTo = FlowScreen.SUMMARY_RETURN,
                    )
                }
                return
            }
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
                        reviewPassApprovedBaseline = it.approved.size,
                        autoNavigateTo = FlowScreen.REVIEW,
                    )
                }
                runReviewLoop()
            }
        }

        /** Back button on the recording ring: abandons the take in progress and returns to
         * wherever this session was launched from — the built list (fresh session) or the
         * summary (rerecording a single approved word), mirroring [skipCurrentWord]'s branching. */
        fun backToListSource() {
            tickerJob?.cancel()
            readySetGoJob?.cancel()
            val state = _uiState.value
            if (state.rerecordIndex != null) {
                _uiState.update {
                    it.copy(
                        recordingQueue = emptyList(),
                        rerecordIndex = null,
                        autoNavigateTo = FlowScreen.SUMMARY_RETURN,
                    )
                }
                return
            }
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

        /** Back button on the review carousel (s-arr): undoes entry into this review pass rather
         * than just popping the nav stack, which would otherwise land on a blank
         * [wiki.asaf.wikisayit.ui.recording.RecordingScreen] — [RecordingFlowUiState.recordingIndex]
         * is left one-past-the-end of [RecordingFlowUiState.recordingQueue] once every take in a
         * pass has been committed to review (see [commitWord]/[skipCurrentWord]/[stopSession]), and
         * this cancels the review loop's auto-advancing decision timer too. Any redo/drop/approve
         * decisions already made this pass are rolled back: [RecordingFlowUiState.redoQueue] is
         * always empty at pass start, and [RecordingFlowUiState.approved] is truncated back to
         * [RecordingFlowUiState.reviewPassApprovedBaseline]. */
        fun backToRecordingFromReview() {
            tickerJob?.cancel()
            _uiState.update {
                it.copy(
                    recordingIndex = (it.reviewSession.size - 1).coerceAtLeast(0),
                    recordingPhase = RecordingPhase.READY,
                    reviewSession = emptyList(),
                    reviewIndex = 0,
                    redoQueue = emptyList(),
                    approved = it.approved.take(it.reviewPassApprovedBaseline),
                    reviewPassApprovedBaseline = 0,
                    autoNavigateTo = FlowScreen.RECORDING,
                )
            }
            beginRecordingForCurrentEntry()
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

        // --- top bar "reset session" (tapping the app name from any screen) ---

        fun askResetSession() {
            _uiState.update { it.copy(showResetSessionDialog = true) }
        }

        fun cancelResetSession() {
            _uiState.update { it.copy(showResetSessionDialog = false) }
        }

        fun confirmResetSession() = resetFlow()

        private fun resetFlow() {
            tickerJob?.cancel()
            replayJob?.cancel()
            readySetGoJob?.cancel()
            manualStopSignal = null
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
         * left on-device, per the design's "recordings stay on device until both steps succeed".
         * Persists the not-yet-complete entries (s-o8f) so [PendingUploadResumer] can pick them
         * up on the next launch instead of losing them once [resetFlow] wipes this session. */
        fun leaveFailuresForLater() {
            tickerJob?.cancel()
            val state = _uiState.value
            val profileId = state.activeProfile?.profile?.id
            val isoCode = state.language?.isoCode
            if (profileId != null && isoCode != null) {
                val items =
                    state.uploadNeedsAttention.mapNotNull { index ->
                        val entry = state.approved.getOrNull(index) ?: return@mapNotNull null
                        val entryState = state.uploadStates.getOrNull(index) ?: return@mapNotNull null
                        PendingUploadItem(
                            entry = entry,
                            profileId = profileId,
                            isoCode = isoCode,
                            username = state.username,
                            speakerName = state.speakerName,
                            dialect = state.dialect,
                            proficiency = state.language?.proficiency,
                            commonsDone = entryState.commonsDone,
                            p443Done = entryState.p443Done,
                            renameSuffix = entryState.renameSuffix,
                        )
                    }
                viewModelScope.launch { pendingUploadRepository.saveForLater(items) }
            }
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
            val dialect = _uiState.value.dialect
            val proficiency = _uiState.value.language?.proficiency
            for (index in indices) {
                val entry = _uiState.value.approved.getOrNull(index) ?: continue
                _uiState.update { it.copy(activeUploadIndex = index) }
                uploadEntry(index, entry, isoCode, username, speakerName, dialect, proficiency, profileId)
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
            dialect: String,
            proficiency: LanguageProficiency?,
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
                        commonsUploader.upload(renamedEntry, isoCode, username, speakerName, dialect, proficiency)
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
                p443StatementWriter.addPronunciation(entry, filename, speakerName, username, dialect)
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
