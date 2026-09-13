package wiki.asaf.wikisayit.data.uploads

import kotlinx.coroutines.CancellationException
import wiki.asaf.wikisayit.data.commons.CommonsUploader
import wiki.asaf.wikisayit.data.local.db.RecordingEntryType
import wiki.asaf.wikisayit.data.stats.StatsRepository
import wiki.asaf.wikisayit.data.wikidata.P443StatementWriter
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.commonsFilename
import javax.inject.Inject

/** Resumes every recording left for later (s-o8f) via [PendingUploadRepository.saveForLater]:
 * retries whichever of the two contribution steps ([CommonsUploader.upload],
 * [P443StatementWriter.addPronunciation]) hasn't succeeded yet, independent of any in-memory
 * session state. One item failing again doesn't stop the rest from being attempted, and progress
 * already made (e.g. Commons succeeded, P443 didn't) is never redone. */
class PendingUploadResumer
    @Inject
    constructor(
        private val repository: PendingUploadRepository,
        private val commonsUploader: CommonsUploader,
        private val p443StatementWriter: P443StatementWriter,
        private val statsRepository: StatsRepository,
    ) {
        suspend fun resumeAll() {
            for (item in repository.loadAll()) {
                resumeOne(item)
            }
        }

        private suspend fun resumeOne(item: PendingUploadItem) {
            val renamedEntry =
                if (item.renameSuffix > 0) {
                    item.entry.copy(label = "${item.entry.label} (${item.renameSuffix + 1})")
                } else {
                    item.entry
                }
            val filename: String
            if (!item.commonsDone) {
                filename =
                    try {
                        commonsUploader.upload(renamedEntry, item.isoCode, item.username, item.speakerName)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        return
                    }
                repository.markCommonsDone(item.id)
            } else {
                filename = renamedEntry.commonsFilename(item.isoCode, item.username, item.speakerName)
            }
            try {
                p443StatementWriter.addPronunciation(item.entry, filename)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return
            }
            repository.markP443Done(item.id)
            val entryType =
                if (item.entry.kind == EntryKind.FORM) RecordingEntryType.LEXEME_FORM else RecordingEntryType.WIKIDATA_ITEM
            statsRepository.recordContribution(item.profileId, entryType)
            repository.delete(item.id)
        }
    }
