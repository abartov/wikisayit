package wiki.asaf.wikisayit.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Where in-progress takes are written during a session: a private cache subdirectory, since
 * these are working files until Contribution uploads the approved ones — safe for the OS to
 * reclaim under storage pressure, and cleaned up implicitly (no persistent bookkeeping needed).
 */
class RecordingFileStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun newRecordingFile(): File {
            val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
            return File(dir, "${UUID.randomUUID()}.ogg")
        }
    }
