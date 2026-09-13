package wiki.asaf.wikisayit.audio

import kotlin.math.abs

/** Peak amplitude of [samples], normalized to the 0f..1f range (1f == ±32768, full scale). */
fun peakAmplitude(samples: ShortArray): Float {
    var peak = 0
    for (sample in samples) {
        val magnitude = abs(sample.toInt())
        if (magnitude > peak) peak = magnitude
    }
    return peak / 32768f
}
