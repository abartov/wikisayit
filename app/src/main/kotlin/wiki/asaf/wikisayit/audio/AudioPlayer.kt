package wiki.asaf.wikisayit.audio

import java.io.File

/**
 * Plays back a recorded take. Abstracted behind an interface so
 * [wiki.asaf.wikisayit.ui.session.RecordingFlowViewModel] can be unit-tested without a real
 * media stack.
 */
interface AudioPlayer {
    /**
     * Plays [file] to completion, suspending until playback ends — naturally, on error, or
     * because cancelling the calling coroutine stopped it early.
     */
    suspend fun play(file: File)
}
