package wiki.asaf.wikisayit.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Plays Ogg Vorbis takes (as written by [OggVorbisEncoder]) via the platform [MediaPlayer] —
 * Android decodes Ogg Vorbis natively, so no extra dependency is needed. Only one take plays at
 * a time: starting a new [play] call stops whatever this instance was already playing.
 */
@Singleton
class AndroidAudioPlayer
    @Inject
    constructor() : AudioPlayer {
        private var current: MediaPlayer? = null

        override suspend fun play(file: File) {
            releaseCurrent()
            suspendCancellableCoroutine { continuation ->
                val player = MediaPlayer()
                current = player
                continuation.invokeOnCancellation { releaseCurrent() }
                player.setOnCompletionListener {
                    releaseCurrent()
                    if (continuation.isActive) continuation.resume(Unit)
                }
                player.setOnErrorListener { _, _, _ ->
                    releaseCurrent()
                    if (continuation.isActive) continuation.resume(Unit)
                    true
                }
                try {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    player.setDataSource(file.absolutePath)
                    player.prepare()
                    player.start()
                } catch (_: Exception) {
                    releaseCurrent()
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }

        private fun releaseCurrent() {
            current?.let { player ->
                runCatching { player.stop() }
                player.release()
            }
            current = null
        }
    }
