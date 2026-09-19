package wiki.asaf.wikisayit.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Level summary of one audio frame, both normalized to the 0f..1f range (1f == ±32768, full scale).
 *
 * @param rms root-mean-square level: the energy the frame actually carries, averaged over all its
 *   samples. Speech detection keys off this rather than [peak] because a single sample decides the
 *   peak, so one click, bump or table knock makes a frame look as loud as a spoken syllable. For
 *   speech, rms typically sits 12-18 dB (4-8x) below peak; for a constant-valued test tone they
 *   are equal.
 * @param peak largest single-sample magnitude in the frame, in the same units [cropSilence] trims by.
 */
data class FrameLevel(val rms: Float, val peak: Float)

/** Computes both levels of [samples] in a single pass. */
fun frameLevel(samples: ShortArray): FrameLevel {
    if (samples.isEmpty()) return FrameLevel(rms = 0f, peak = 0f)
    var peak = 0
    var sumOfSquares = 0.0
    for (sample in samples) {
        val value = sample.toInt()
        val magnitude = abs(value)
        if (magnitude > peak) peak = magnitude
        sumOfSquares += value.toDouble() * value.toDouble()
    }
    return FrameLevel(
        rms = (sqrt(sumOfSquares / samples.size) / 32768.0).toFloat(),
        peak = peak / 32768f,
    )
}
