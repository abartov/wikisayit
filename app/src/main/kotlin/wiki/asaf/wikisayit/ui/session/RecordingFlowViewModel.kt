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
import javax.inject.Inject
import kotlin.math.max

private const val READY_PHASE_MILLIS = 600L
private const val SPEAKING_PHASE_MILLIS = 1200L
private const val TICK_MILLIS = 100L
private const val CHECK_TICK_MILLIS = 120L
private const val REVIEW_PLAYING_MILLIS = 1200L
private const val REVIEW_DECISION_SECONDS = 1.5f
private const val UPLOAD_STEP_MILLIS = 500L

/**
 * Drives the whole recording flow (Profile through Done) from one activity-scoped ViewModel.
 * List building against live Wikidata/SPARQL/categories (s-dbm.3, s-dbm.4) and the P443
 * existence check (s-dbm.5) aren't implemented yet, so [buildList] and [startCheck] simulate
 * realistic timing without fabricating results they can't back up: a pasted list is split
 * line-by-line for real, a query/category source seeds a small placeholder queue, and the
 * check always passes the raw list through unchanged (0 excluded) until the real checker
 * lands. The recording/review timing loops are likewise simulated pending the audio engine
 * (s-7lo) — [RecordingBlocker] and the phase timings are the seams that work will plug into.
 */
@HiltViewModel
class RecordingFlowViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        private val settingsRepository: SettingsRepository,
        private val statsRepository: StatsRepository,
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
            val entries =
                when (state.listSourceType) {
                    ListSourceType.PASTE -> parsePastedLines(state.sourceText, state.matchAs)
                    else -> placeholderQueue()
                }
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

        private fun parsePastedLines(
            text: String,
            matchAs: MatchAs,
        ): List<QueueEntry> =
            text.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { index, line ->
                if (matchAs == MatchAs.ITEMS) {
                    QueueEntry(
                        label = line,
                        kind = EntryKind.ITEM,
                        detail = "Wikidata item",
                        qid = "Q${100000 + index}",
                    )
                } else {
                    val lexemeId = "L${200000 + index}"
                    QueueEntry(
                        label = line,
                        kind = EntryKind.FORM,
                        detail = "lexeme form",
                        lexemeId = lexemeId,
                        formId = "$lexemeId-F1",
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
                    val total = _uiState.value.rawCount
                    while (_uiState.value.checkDone < total) {
                        delay(CHECK_TICK_MILLIS)
                        _uiState.update { it.copy(checkDone = it.checkDone + 1) }
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
                    it.copy(reviewSession = it.recordingQueue, reviewIndex = 0, redoQueue = emptyList())
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
                _uiState.update { it.copy(listBuildStage = ListBuildStage.FRESH, recordingQueue = emptyList()) }
                return
            }
            if (state.recordingIndex >= queue.size) {
                _uiState.update {
                    it.copy(recordingQueue = queue, reviewSession = queue, reviewIndex = 0, redoQueue = emptyList())
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
                    )
                }
            } else {
                _uiState.update { it.copy(reviewSession = done, reviewIndex = 0, redoQueue = emptyList()) }
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
                        )
                    }
                    runRecordingLoop()
                } else {
                    _uiState.update { it.copy(approved = approved, redoQueue = emptyList()) }
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
                    _uiState.update { it.copy(uploadIndex = approved.size) }
                }
        }
    }
