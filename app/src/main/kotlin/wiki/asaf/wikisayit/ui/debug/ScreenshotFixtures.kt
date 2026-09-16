package wiki.asaf.wikisayit.ui.debug

import wiki.asaf.wikisayit.data.local.db.LanguageProficiency
import wiki.asaf.wikisayit.data.local.db.ProfileLanguageEntity
import wiki.asaf.wikisayit.data.local.db.RecordingEntryType
import wiki.asaf.wikisayit.data.local.db.RecordingStatDao
import wiki.asaf.wikisayit.data.local.db.RecordingStatEntity
import wiki.asaf.wikisayit.data.local.db.SpeakerProfileEntity
import wiki.asaf.wikisayit.data.local.db.SpeakerProfileWithLanguages
import wiki.asaf.wikisayit.data.local.settings.SettingsRepository
import wiki.asaf.wikisayit.data.profile.ProfileLanguageInput
import wiki.asaf.wikisayit.data.profile.ProfileRepository
import wiki.asaf.wikisayit.ui.navigation.WikiSayItRoute
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.ListBuildStage
import wiki.asaf.wikisayit.ui.session.ListSourceType
import wiki.asaf.wikisayit.ui.session.QueueEntry
import wiki.asaf.wikisayit.ui.session.RecordingFlowUiState
import wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel
import wiki.asaf.wikisayit.ui.session.RecordingPhase
import wiki.asaf.wikisayit.ui.session.SelectedLanguage
import wiki.asaf.wikisayit.ui.session.UploadEntryState
import wiki.asaf.wikisayit.ui.session.UploadFailureStatus
import wiki.asaf.wikisayit.ui.session.UploadStepFailure

/**
 * Debug-only screenshot harness: lets [wiki.asaf.wikisayit.MainActivity] jump straight to any
 * phase of the app flow with realistic, simulated data instead of driving the real OAuth/network
 * flow through it. Triggered by launching with a string extra named [SCREENSHOT_TARGET_EXTRA]
 * whose value is one of [ScreenshotTarget]'s names, e.g.:
 * `adb shell am start -n wiki.asaf.wikisayit/.MainActivity -e wiki.asaf.wikisayit.SCREENSHOT_TARGET RECORDING`
 * Only ever wired up behind `BuildConfig.DEBUG` (see [RecordingFlowViewModel.debugApplyState]),
 * so none of this reaches a release build's behavior.
 */
const val SCREENSHOT_TARGET_EXTRA = "wiki.asaf.wikisayit.SCREENSHOT_TARGET"

enum class ScreenshotTarget {
    SIGN_IN,
    PROFILE,
    NEW_PROFILE,
    LANGUAGE,
    LIST_SOURCE,
    RECORDING,
    REVIEW,
    SESSION_SUMMARY,
    CONTRIBUTION,
    DONE,
    SETTINGS,
    STATS,
    ABOUT,
}

private val SAMPLE_PROFILE =
    SpeakerProfileWithLanguages(
        profile = SpeakerProfileEntity(id = 1, wikimediaUsername = "Ijon"),
        languages =
            listOf(
                ProfileLanguageEntity(
                    id = 1,
                    profileId = 1,
                    isoCode = "he",
                    languageName = "Hebrew",
                    proficiency = LanguageProficiency.NATIVE,
                ),
                ProfileLanguageEntity(
                    id = 2,
                    profileId = 1,
                    isoCode = "en",
                    languageName = "English",
                    proficiency = LanguageProficiency.NATIVE,
                ),
            ),
    )

private val SAMPLE_LANGUAGE =
    SelectedLanguage(isoCode = "he", name = "Hebrew", proficiency = LanguageProficiency.NATIVE, dialect = "")

/** Mirrors the DESIGN.md examples (lexeme L708539/water L3302, item Q432522) in shape, with
 * fictitious ids/labels standing in for real Wikidata content. */
private val SAMPLE_QUEUE =
    listOf(
        QueueEntry(label = "יונתן רטוש", kind = EntryKind.ITEM, detail = "Wikidata item · poet", qid = "Q432522"),
        QueueEntry(label = "ירושלים", kind = EntryKind.ITEM, detail = "Wikidata item · city", qid = "Q1218"),
        QueueEntry(
            label = "מורה",
            kind = EntryKind.FORM,
            detail = "lexeme form · noun",
            lexemeId = "L123456",
            formId = "L123456-F1",
        ),
        QueueEntry(
            label = "ללכת",
            kind = EntryKind.FORM,
            detail = "lexeme form · verb",
            lexemeId = "L234567",
            formId = "L234567-F1",
        ),
        QueueEntry(label = "שולחן", kind = EntryKind.ITEM, detail = "Wikidata item · object", qid = "Q14748"),
    )

/** The [RecordingFlowUiState] to apply for [target], or null when the target's screen either
 * needs no fixture (a static screen) or reads its data from elsewhere (seeded separately by
 * [applyScreenshotTarget]). */
private fun fixtureFor(target: ScreenshotTarget): RecordingFlowUiState? =
    when (target) {
        ScreenshotTarget.LANGUAGE -> RecordingFlowUiState(activeProfile = SAMPLE_PROFILE)
        ScreenshotTarget.LIST_SOURCE ->
            RecordingFlowUiState(
                activeProfile = SAMPLE_PROFILE,
                language = SAMPLE_LANGUAGE,
                listSourceType = ListSourceType.PASTE,
                listBuildStage = ListBuildStage.CHECKED,
                rawCount = 8,
                checkDone = 8,
                excludedCount = 3,
                formsAddedCount = 2,
            )
        ScreenshotTarget.RECORDING ->
            RecordingFlowUiState(
                activeProfile = SAMPLE_PROFILE,
                language = SAMPLE_LANGUAGE,
                recordingQueue = SAMPLE_QUEUE,
                recordingIndex = 2,
                recordingPass = 1,
                recordingPhase = RecordingPhase.SPEAKING,
            )
        ScreenshotTarget.REVIEW ->
            RecordingFlowUiState(
                activeProfile = SAMPLE_PROFILE,
                language = SAMPLE_LANGUAGE,
                reviewSession = SAMPLE_QUEUE.take(4).map { it.copy(durationSeconds = 1.6f) },
                reviewIndex = 1,
                reviewPlaying = false,
                reviewDecisionRemainingSeconds = 0.9f,
            )
        ScreenshotTarget.SESSION_SUMMARY ->
            RecordingFlowUiState(
                activeProfile = SAMPLE_PROFILE,
                language = SAMPLE_LANGUAGE,
                approved = SAMPLE_QUEUE.take(4),
            )
        ScreenshotTarget.CONTRIBUTION ->
            RecordingFlowUiState(
                activeProfile = SAMPLE_PROFILE,
                language = SAMPLE_LANGUAGE,
                approved = SAMPLE_QUEUE.take(4),
                uploadStates =
                    listOf(
                        UploadEntryState(commonsDone = true, categoriesDone = true, p443Done = true),
                        UploadEntryState(commonsDone = true, categoriesDone = true, p443Done = false),
                        UploadEntryState(
                            failedStep = UploadStepFailure.COMMONS,
                            failureStatus = UploadFailureStatus.NAME_TAKEN,
                            errorMessage = "A file with this name already exists on Commons.",
                        ),
                        UploadEntryState(),
                    ),
                activeUploadIndex = 1,
            )
        ScreenshotTarget.DONE ->
            SAMPLE_QUEUE.take(4).let { approved ->
                RecordingFlowUiState(
                    activeProfile = SAMPLE_PROFILE,
                    language = SAMPLE_LANGUAGE,
                    approved = approved,
                    uploadStates =
                        approved.map {
                            UploadEntryState(commonsDone = true, categoriesDone = true, p443Done = true)
                        },
                )
            }
        ScreenshotTarget.SIGN_IN, ScreenshotTarget.PROFILE, ScreenshotTarget.NEW_PROFILE,
        ScreenshotTarget.SETTINGS, ScreenshotTarget.STATS, ScreenshotTarget.ABOUT,
        -> null
    }

private fun routeFor(target: ScreenshotTarget): WikiSayItRoute =
    when (target) {
        ScreenshotTarget.SIGN_IN -> WikiSayItRoute.SignIn()
        ScreenshotTarget.PROFILE -> WikiSayItRoute.Profile
        ScreenshotTarget.NEW_PROFILE -> WikiSayItRoute.NewProfile
        ScreenshotTarget.LANGUAGE -> WikiSayItRoute.Language
        ScreenshotTarget.LIST_SOURCE -> WikiSayItRoute.ListSource
        ScreenshotTarget.RECORDING -> WikiSayItRoute.Recording
        ScreenshotTarget.REVIEW -> WikiSayItRoute.Review
        ScreenshotTarget.SESSION_SUMMARY -> WikiSayItRoute.SessionSummary
        ScreenshotTarget.CONTRIBUTION -> WikiSayItRoute.Contribution
        ScreenshotTarget.DONE -> WikiSayItRoute.Done
        ScreenshotTarget.SETTINGS -> WikiSayItRoute.Settings
        ScreenshotTarget.STATS -> WikiSayItRoute.Stats
        ScreenshotTarget.ABOUT -> WikiSayItRoute.About
    }

/** Two profiles with a mix of proficiencies/dialects, real rows in the local Room DB so the
 * Profile screen's per-profile recording counts (also DB-backed) resolve correctly. */
private suspend fun seedProfiles(profileRepository: ProfileRepository): Long {
    profileRepository.saveProfile(
        wikimediaUsername = "Yael",
        languages =
            listOf(
                ProfileLanguageInput(
                    isoCode = "yi",
                    languageName = "Yiddish",
                    proficiency = LanguageProficiency.PROFICIENT,
                    dialect = "Litvish",
                ),
            ),
    )
    return profileRepository.saveProfile(
        wikimediaUsername = "Ijon",
        languages =
            listOf(
                ProfileLanguageInput(isoCode = "he", languageName = "Hebrew", proficiency = LanguageProficiency.NATIVE),
                ProfileLanguageInput(
                    isoCode = "en",
                    languageName = "English",
                    proficiency = LanguageProficiency.NATIVE,
                ),
            ),
    )
}

/** Real rows in `recording_stats`, spread across a few months this year and one last year, so
 * the Stats screen's per-month/per-year breakdown has more than a single bucket to show. */
private suspend fun seedStats(
    profileRepository: ProfileRepository,
    recordingStatDao: RecordingStatDao,
) {
    val profileId = seedProfiles(profileRepository)
    val now = System.currentTimeMillis()
    val monthMillis = 30L * 24 * 60 * 60 * 1000
    val plan =
        listOf(
            Triple(0, RecordingEntryType.WIKIDATA_ITEM, 5),
            Triple(0, RecordingEntryType.LEXEME_FORM, 3),
            Triple(1, RecordingEntryType.WIKIDATA_ITEM, 4),
            Triple(2, RecordingEntryType.LEXEME_FORM, 6),
            Triple(14, RecordingEntryType.WIKIDATA_ITEM, 10),
        )
    for ((monthsAgo, entryType, count) in plan) {
        repeat(count) {
            recordingStatDao.insert(
                RecordingStatEntity(
                    profileId = profileId,
                    entryType = entryType,
                    timestampMillis = now - monthsAgo * monthMillis,
                ),
            )
        }
    }
}

/** Applies whatever real seeding [target] needs (Room-backed profiles/stats, or a settings
 * tweak) plus its [RecordingFlowUiState] fixture if it has one, then returns the route to
 * navigate to. Called once from [wiki.asaf.wikisayit.MainActivity] behind `BuildConfig.DEBUG`. */
suspend fun applyScreenshotTarget(
    target: ScreenshotTarget,
    sessionViewModel: RecordingFlowViewModel,
    profileRepository: ProfileRepository,
    settingsRepository: SettingsRepository,
    recordingStatDao: RecordingStatDao,
): WikiSayItRoute {
    // Screenshotting from a warm install must not be derailed by an auto-use-last-profile
    // setting left on by an earlier SETTINGS screenshot.
    if (target != ScreenshotTarget.SETTINGS) {
        settingsRepository.setAutoUseLastProfile(false)
    }
    when (target) {
        ScreenshotTarget.PROFILE -> seedProfiles(profileRepository)
        ScreenshotTarget.STATS -> seedStats(profileRepository, recordingStatDao)
        ScreenshotTarget.SETTINGS -> {
            settingsRepository.setAutoUseLastProfile(true)
            settingsRepository.setTrimSilenceAutomatically(true)
        }
        else -> Unit
    }
    fixtureFor(target)?.let(sessionViewModel::debugApplyState)
    return routeFor(target)
}
