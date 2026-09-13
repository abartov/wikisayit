package wiki.asaf.wikisayit.audio

import com.github.axet.vorbisjni.Vorbis
import java.io.File
import java.io.FileOutputStream

/**
 * Encodes mono PCM-16 recordings to Ogg Vorbis using the native `libvorbis` JNI binding from
 * `com.github.axet:vorbis` (LGPL-3.0, see CREDITS).
 *
 * @param quality Vorbis quality factor, -0.1f (smallest/worst) to 1f (largest/best); 0.4f is
 *   the value axet's own FormatOGG example uses and is a reasonable default for speech.
 */
class OggVorbisEncoder(
    private val quality: Float = 0.4f,
) : AudioEncoder {
    override fun encode(
        samples: ShortArray,
        sampleRate: Int,
        output: File,
    ) {
        loadNativeLibraries()
        val vorbis = Vorbis()
        try {
            vorbis.open(CHANNELS, sampleRate, quality)
            FileOutputStream(output).use { out ->
                out.write(vorbis.encode(samples, 0, samples.size))
                // A null/zero-length call flushes the encoder's trailing Ogg pages.
                out.write(vorbis.encode(null, 0, 0))
            }
        } catch (e: UnsatisfiedLinkError) {
            throw AudioEncodingException("Native Vorbis encoder unavailable", e)
        } finally {
            vorbis.close()
        }
    }

    companion object {
        private const val CHANNELS = 1

        @Volatile
        private var librariesLoaded = false

        @Synchronized
        private fun loadNativeLibraries() {
            if (librariesLoaded) return
            try {
                System.loadLibrary("vorbis")
                System.loadLibrary("vorbisjni")
                librariesLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                throw AudioEncodingException("Failed to load native Vorbis libraries", e)
            }
        }
    }
}
