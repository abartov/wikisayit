package wiki.asaf.wikisayit.audio

/** Silence padding added to both ends of a cropped recording, matching LinguaRecorder's behavior. */
const val DEFAULT_PADDING_SECONDS = 0.2f

/** Adds [paddingSeconds] of digital silence to both the start and end of [samples]. */
fun addPadding(
    samples: ShortArray,
    sampleRate: Int,
    paddingSeconds: Float = DEFAULT_PADDING_SECONDS,
): ShortArray {
    val paddingSamples = (paddingSeconds * sampleRate).toInt()
    if (paddingSamples <= 0) return samples

    val result = ShortArray(paddingSamples + samples.size + paddingSamples)
    samples.copyInto(result, destinationOffset = paddingSamples)
    return result
}
