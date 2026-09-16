package wiki.asaf.wikisayit.ui.session

import wiki.asaf.wikisayit.data.local.db.SpeakerProfileWithLanguages
import wiki.asaf.wikisayit.data.local.settings.AppSettings

/**
 * Single source of truth for the whole recording flow (Profile through Done). Shared across
 * routes via one activity-scoped ViewModel because transitions like "stop with zero takes
 * skips review" or "a redo pass only reviews the redone words" span several routes at once.
 */
data class RecordingFlowUiState(
    val profiles: List<SpeakerProfileWithLanguages> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val activeProfile: SpeakerProfileWithLanguages? = null,
    val language: SelectedLanguage? = null,
    // --- list sourcing (step 3 of 4) ---
    val listSourceType: ListSourceType? = null,
    val listBuildStage: ListBuildStage = ListBuildStage.PICK_SOURCE,
    val sourceText: String = "",
    val matchAs: MatchAs = MatchAs.ITEMS,
    val categoryDepth: CategoryDepth = CategoryDepth.TWO,
    val rawCount: Int = 0,
    val resolveDone: Int = 0,
    /** True when the most recent list build hit a network failure it couldn't recover from —
     * lets the UI tell a genuinely empty/complete result apart from one where fetching silently
     * dropped data (s-fns). */
    val listBuildHadError: Boolean = false,
    val checkDone: Int = 0,
    val excludedCount: Int = 0,
    val formsAddedCount: Int = 0,
    val finalQueue: List<QueueEntry> = emptyList(),
    // --- disambiguation ---
    val disambiguationQueue: List<DisambiguationCase> = emptyList(),
    val disambiguationIndex: Int = 0,
    // --- recording ---
    val recordingQueue: List<QueueEntry> = emptyList(),
    val recordingIndex: Int = 0,
    val recordingPass: Int = 1,
    val recordingPhase: RecordingPhase = RecordingPhase.READY,
    /** Non-null only during the once-per-session "ready, set, go" pre-roll played by
     * [RecordingFlowViewModel.startSession] before the mic starts listening. */
    val readySetGoPhase: ReadySetGoPhase? = null,
    val silenceRemainingSeconds: Float = 0f,
    val recordingBlocker: RecordingBlocker? = null,
    /** Manual record/stop/next control (s-3fe), useful in noisy environments where automatic
     * speech-onset/silence detection isn't reliable. */
    val manualMode: Boolean = false,
    /** True while a manual-mode take is being captured, between [RecordingFlowViewModel.startManualRecording]
     * and [RecordingFlowViewModel.stopManualRecording]. */
    val manualRecordingActive: Boolean = false,
    // --- review ---
    val reviewSession: List<QueueEntry> = emptyList(),
    val reviewIndex: Int = 0,
    val reviewPlaying: Boolean = true,
    val reviewDecisionRemainingSeconds: Float = 1.5f,
    val redoQueue: List<QueueEntry> = emptyList(),
    val approved: List<QueueEntry> = emptyList(),
    /** [approved]'s length at the moment the current review pass began — lets
     * [RecordingFlowViewModel.backToRecordingFromReview] truncate off only this pass's
     * auto-approved entries, not ones carried over from an earlier pass. */
    val reviewPassApprovedBaseline: Int = 0,
    /** Set while [wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel.rerecordApprovedEntry] is
     * re-recording a single already-approved entry from the summary screen: the index into
     * [approved] that the finished take will replace. Recording a full new [approved] entry from
     * scratch never sets this. */
    val rerecordIndex: Int? = null,
    val showAbandonDialog: Boolean = false,
    /** True while the "reset session?" confirmation triggered by tapping the top bar's app name
     * ([wiki.asaf.wikisayit.ui.scaffold.WikiSayItAppScaffold]) is open. Distinct from
     * [showAbandonDialog], which is scoped to the summary screen's approved-count-aware copy. */
    val showResetSessionDialog: Boolean = false,
    /** Set while an on-demand replay ([wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel.replayEntry])
     * is playing, so the triggering row can show it's active. */
    val replayingEntryId: String? = null,
    // --- upload (2e: per-entry state so contribution failures can be surfaced and retried) ---
    val uploadStates: List<UploadEntryState> = emptyList(),
    val activeUploadIndex: Int? = null,
    val connectivityLost: Boolean = false,
    // --- navigation ---
    val autoNavigateTo: FlowScreen? = null,
) {
    val currentDisambiguation: DisambiguationCase?
        get() = disambiguationQueue.getOrNull(disambiguationIndex)

    val currentRecordingEntry: QueueEntry?
        get() = recordingQueue.getOrNull(recordingIndex)

    val currentReviewEntry: QueueEntry?
        get() = reviewSession.getOrNull(reviewIndex)

    val username: String
        get() = activeProfile?.profile?.wikimediaUsername.orEmpty()

    val speakerName: String
        get() = activeProfile?.profile?.speakerName.orEmpty()

    val dialect: String
        get() = language?.dialect.orEmpty()

    val uploadDoneCount: Int
        get() = uploadStates.count { it.isComplete }

    val uploadNeedsAttention: List<Int>
        get() = uploadStates.indices.filter { uploadStates[it].needsAttention }

    val activeUploadEntry: QueueEntry?
        get() = activeUploadIndex?.let { approved.getOrNull(it) }

    val activeUploadState: UploadEntryState?
        get() = activeUploadIndex?.let { uploadStates.getOrNull(it) }
}
