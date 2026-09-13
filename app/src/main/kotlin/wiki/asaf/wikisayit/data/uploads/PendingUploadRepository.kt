package wiki.asaf.wikisayit.data.uploads

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import wiki.asaf.wikisayit.data.local.db.PendingUploadDao
import wiki.asaf.wikisayit.data.local.db.PendingUploadEntity
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.QueueEntry
import java.io.File
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

private const val SCRIPT_VARIANTS_DELIMITER = ""

/** One approved recording left for later (s-o8f): the [entry] it belongs to (with [QueueEntry.audioFile]
 * pointing at durable storage, not the cache directory it was recorded into) plus the contribution
 * context ([profileId]/[isoCode]/[username]/[speakerName]) and per-entry upload progress needed to
 * resume without redoing already-finished steps. */
data class PendingUploadItem(
    val id: Long = 0,
    val entry: QueueEntry,
    val profileId: Long,
    val isoCode: String,
    val username: String,
    val speakerName: String,
    val commonsDone: Boolean = false,
    val p443Done: Boolean = false,
    val renameSuffix: Int = 0,
)

interface PendingUploadRepository {
    /** Moves each item's audio file into durable storage and persists it. Items whose audio file
     * is already gone are silently skipped — there's nothing left to retry. */
    suspend fun saveForLater(items: List<PendingUploadItem>)

    suspend fun loadAll(): List<PendingUploadItem>

    suspend fun markCommonsDone(id: Long)

    suspend fun markP443Done(id: Long)

    /** Deletes the row and its durable audio file copy. */
    suspend fun delete(id: Long)
}

class RoomPendingUploadRepository
    @Inject
    constructor(
        private val pendingUploadDao: PendingUploadDao,
        @ApplicationContext private val context: Context,
        private val clock: Clock = Clock.systemUTC(),
    ) : PendingUploadRepository {
        private val pendingUploadsDir: File
            get() = File(context.filesDir, "pending_uploads").apply { mkdirs() }

        override suspend fun saveForLater(items: List<PendingUploadItem>) {
            for (item in items) {
                val sourceFile = item.entry.audioFile ?: continue
                if (!sourceFile.exists()) continue
                val durableFile = File(pendingUploadsDir, "${UUID.randomUUID()}.ogg")
                if (!sourceFile.renameTo(durableFile)) continue
                pendingUploadDao.insert(item.toEntity(durableFile.absolutePath, clock.millis()))
            }
        }

        override suspend fun loadAll(): List<PendingUploadItem> =
            pendingUploadDao.getAll().mapNotNull { entity ->
                val audioFile = File(entity.audioFilePath)
                if (!audioFile.exists()) {
                    pendingUploadDao.deleteById(entity.id)
                    null
                } else {
                    entity.toItem(audioFile)
                }
            }

        override suspend fun markCommonsDone(id: Long) = updateEntity(id) { it.copy(commonsDone = true) }

        override suspend fun markP443Done(id: Long) = updateEntity(id) { it.copy(p443Done = true) }

        override suspend fun delete(id: Long) {
            val entity = pendingUploadDao.getById(id) ?: return
            File(entity.audioFilePath).delete()
            pendingUploadDao.deleteById(id)
        }

        private suspend fun updateEntity(
            id: Long,
            transform: (PendingUploadEntity) -> PendingUploadEntity,
        ) {
            val entity = pendingUploadDao.getById(id) ?: return
            pendingUploadDao.update(transform(entity))
        }
    }

private fun PendingUploadItem.toEntity(
    audioFilePath: String,
    createdAtMillis: Long,
) = PendingUploadEntity(
    label = entry.label,
    kind = entry.kind.name,
    qid = entry.qid,
    lexemeId = entry.lexemeId,
    formId = entry.formId,
    scriptVariants = entry.scriptVariants.joinToString(SCRIPT_VARIANTS_DELIMITER),
    audioFilePath = audioFilePath,
    profileId = profileId,
    isoCode = isoCode,
    username = username,
    speakerName = speakerName,
    commonsDone = commonsDone,
    p443Done = p443Done,
    renameSuffix = renameSuffix,
    createdAtMillis = createdAtMillis,
)

private fun PendingUploadEntity.toItem(audioFile: File) =
    PendingUploadItem(
        id = id,
        entry =
            QueueEntry(
                label = label,
                kind = EntryKind.valueOf(kind),
                detail = "",
                qid = qid,
                lexemeId = lexemeId,
                formId = formId,
                audioFile = audioFile,
                scriptVariants = if (scriptVariants.isEmpty()) emptyList() else scriptVariants.split(SCRIPT_VARIANTS_DELIMITER),
            ),
        profileId = profileId,
        isoCode = isoCode,
        username = username,
        speakerName = speakerName,
        commonsDone = commonsDone,
        p443Done = p443Done,
        renameSuffix = renameSuffix,
    )
