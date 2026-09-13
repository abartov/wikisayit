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
    val silenceRemainingSeconds: Float = 0f,
    val recordingBlocker: RecordingBlocker? = null,
    // --- review ---
    val reviewSession: List<QueueEntry> = emptyList(),
    val reviewIndex: Int = 0,
    val reviewPlaying: Boolean = true,
    val reviewDecisionRemainingSeconds: Float = 1.5f,
    val redoQueue: List<QueueEntry> = emptyList(),
    val approved: List<QueueEntry> = emptyList(),
    val showAbandonDialog: Boolean = false,
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

    val uploadDoneCount: Int
        get() = uploadStates.count { it.isComplete }

    val uploadNeedsAttention: List<Int>
        get() = uploadStates.indices.filter { uploadStates[it].needsAttention }

    val activeUploadEntry: QueueEntry?
        get() = activeUploadIndex?.let { approved.getOrNull(it) }

    val activeUploadState: UploadEntryState?
        get() = activeUploadIndex?.let { uploadStates.getOrNull(it) }
}
