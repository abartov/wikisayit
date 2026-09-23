package wiki.asaf.wikisayit.audio

import kotlin.math.abs

/** Amplitude (0f..1f) below which a sample counts as silence/background noise for cropping. */
const val DEFAULT_CROP_THRESHOLD = 0.04f

/**
 * Original audio kept on each side of the detected speech when cropping, so the soft onset of the
 * first sound and the tail of the last one — often below the crop threshold — aren't clipped.
 */
const val DEFAULT_CROP_MARGIN_SECONDS = 0.2f

/**
 * Locates the speech in a finished recording, ported from LinguaRecorder's
 * AudioSamples.lTrim()/rTrim() (~/dev/LinguaRecorder/src/RecordingProcessor.js).
 *
 * Scans from both ends for the first sample whose amplitude exceeds [threshold]. Returns the
 * index range between them, or null if the whole recording is below threshold.
 */
fun speechBounds(
    samples: ShortArray,
    threshold: Float = DEFAULT_CROP_THRESHOLD,
): IntRange? {
    val cutoff = (threshold * 32768f).toInt()

    var start = 0
    while (start < samples.size && abs(samples[start].toInt()) <= cutoff) {
        start++
    }
    if (start == samples.size) return null

    var end = samples.size - 1
    while (end > start && abs(samples[end].toInt()) <= cutoff) {
        end--
    }

    return start..end
}

/**
 * Trims leading and trailing silence/background noise from a finished recording, keeping up to
 * [marginSamples] of the original audio on each side of the [speechBounds] (fewer where the
 * recording doesn't extend that far). Returns an empty array if the whole recording is below
 * threshold.
 */
fun cropSilence(
    samples: ShortArray,
    threshold: Float = DEFAULT_CROP_THRESHOLD,
    marginSamples: Int = 0,
): ShortArray {
    val bounds = speechBounds(samples, threshold) ?: return ShortArray(0)
    return cropTo(samples, bounds, marginSamples)
}

/** [samples] within [bounds], widened by up to [marginSamples] on each side. */
fun cropTo(
    samples: ShortArray,
    bounds: IntRange,
    marginSamples: Int,
): ShortArray =
    samples.copyOfRange(
        (bounds.first - marginSamples).coerceAtLeast(0),
        (bounds.last + 1 + marginSamples).coerceAtMost(samples.size),
    )
