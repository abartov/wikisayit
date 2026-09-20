package wiki.asaf.wikisayit.data.skipped

import kotlinx.coroutines.flow.Flow
import wiki.asaf.wikisayit.data.local.db.SkippedEntryDao
import wiki.asaf.wikisayit.data.local.db.SkippedEntryEntity
import wiki.asaf.wikisayit.ui.session.QueueEntry
import java.time.Clock
import javax.inject.Inject

/**
 * Remembers the entries a speaker skipped while recording, so category list builds can leave them
 * out next time (s-fi0.2). Without this, the cap on list size means a skipped word keeps coming
 * back at the head of every list built from the same category.
 */
interface SkippedEntryRepository {
    suspend fun remember(entry: QueueEntry)

    /** Evidence ids (QIDs, form ids) of every entry skipped so far. */
    suspend fun skippedIds(): Set<String>

    fun observeCount(): Flow<Int>

    suspend fun clear()
}

class RoomSkippedEntryRepository
    @Inject
    constructor(
        private val skippedEntryDao: SkippedEntryDao,
        private val clock: Clock = Clock.systemUTC(),
    ) : SkippedEntryRepository {
        /** Entries that never resolved to a Wikidata id have nothing stable to remember them by,
         * so they're dropped rather than stored under an empty key. */
        override suspend fun remember(entry: QueueEntry) {
            val entityId = entry.evidenceId
            if (entityId.isBlank()) return
            skippedEntryDao.insert(
                SkippedEntryEntity(entityId = entityId, label = entry.label, skippedAtMillis = clock.millis()),
            )
        }

        override suspend fun skippedIds(): Set<String> = skippedEntryDao.getAllIds().toSet()

        override fun observeCount(): Flow<Int> = skippedEntryDao.observeCount()

        override suspend fun clear() = skippedEntryDao.deleteAll()
    }
