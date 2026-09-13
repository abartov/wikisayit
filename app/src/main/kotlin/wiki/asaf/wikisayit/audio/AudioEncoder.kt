package wiki.asaf.wikisayit.audio

import java.io.File

/** Converts a finished mono PCM-16 buffer into an encoded audio file. */
interface AudioEncoder {
    fun encode(
        samples: ShortArray,
        sampleRate: Int,
        output: File,
    )
}

class AudioEncodingException(message: String, cause: Throwable? = null) : Exception(message, cause)
