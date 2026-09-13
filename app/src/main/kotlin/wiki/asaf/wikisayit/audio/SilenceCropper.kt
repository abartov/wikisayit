package wiki.asaf.wikisayit.audio

import kotlin.math.abs

/** Amplitude (0f..1f) below which a sample counts as silence/background noise for cropping. */
const val DEFAULT_CROP_THRESHOLD = 0.04f

/**
 * Trims leading and trailing silence/background noise from a finished recording, ported from
 * LinguaRecorder's AudioSamples.lTrim()/rTrim() (~/dev/LinguaRecorder/src/RecordingProcessor.js).
 *
 * Scans from both ends for the first sample whose amplitude exceeds [threshold] and cuts
 * everything before/after it. Returns an empty array if the whole recording is below threshold.
 */
fun cropSilence(
    samples: ShortArray,
    threshold: Float = DEFAULT_CROP_THRESHOLD,
): ShortArray {
    if (samples.isEmpty()) return samples
    val cutoff = (threshold * 32768f).toInt()

    var start = 0
    while (start < samples.size && abs(samples[start].toInt()) <= cutoff) {
        start++
    }
    if (start == samples.size) return ShortArray(0)

    var end = samples.size - 1
    while (end > start && abs(samples[end].toInt()) <= cutoff) {
        end--
    }

    return samples.copyOfRange(start, end + 1)
}
