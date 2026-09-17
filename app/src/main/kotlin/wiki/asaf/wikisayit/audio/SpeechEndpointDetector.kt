package wiki.asaf.wikisayit.audio

enum class SpeechState { LISTENING, SPEAKING, TRAILING_SILENCE }

data class SpeechDetectorConfig(
    /** Peak amplitude (0f..1f) that counts as the start of speech while [SpeechState.LISTENING]. */
    val startThreshold: Float = 0.08f,
    /** Peak amplitude (0f..1f) below which a frame counts as silence once speech has started. */
    val stopThreshold: Float = 0.04f,
)

private const val NOISE_FLOOR_ALPHA = 0.2f

/** Ambient LISTENING frames needed before the noise floor estimate is trusted over the static
 * [SpeechDetectorConfig] thresholds — below this, a single loud frame could skew the estimate. */
private const val MIN_CALIBRATION_FRAMES = 5

/** How far above the measured noise floor the effective start/stop thresholds are raised, so a
 * room with a nonzero background level doesn't get stuck never seeing amplitude drop back below
 * a threshold set for near-total silence. */
private const val NOISE_FLOOR_STOP_MARGIN = 2.0f
private const val NOISE_FLOOR_START_MARGIN = 4.0f

/** Effective stop threshold is capped to this fraction of the effective start threshold, keeping
 * a meaningful gap between "is speaking" and "is silent" even in a noisy room. */
private const val STOP_THRESHOLD_HEADROOM = 0.75f

sealed interface SpeechTransition {
    data object None : SpeechTransition

    data object StartedSpeaking : SpeechTransition

    data object ResumedSpeaking : SpeechTransition

    data class SilenceProgress(val remainingSeconds: Float) : SpeechTransition

    data object AutoStop : SpeechTransition
}

/**
 * Tracks speech onset and trailing silence across a stream of audio frames, ported from
 * LinguaRecorder's RecordingProcessor autoStart/autoStop state machine
 * (~/dev/LinguaRecorder/src/RecordingProcessor.js).
 *
 * While [SpeechState.LISTENING], no auto-stop timer runs — a word screen with no speech at
 * all is left to the user to abandon, per the product spec (auto-stop only follows speech).
 *
 * The LISTENING frames before speech onset double as an ambient noise sample: their amplitude
 * feeds a running noise floor estimate that raises the effective start/stop thresholds above
 * [SpeechDetectorConfig]'s static defaults in a room with a nonzero background level — otherwise
 * amplitude can sit above [SpeechDetectorConfig.stopThreshold] indefinitely after the user stops
 * talking, and auto-stop never triggers. The estimate freezes once speech starts (LISTENING ends).
 */
class SpeechEndpointDetector(
    private val config: SpeechDetectorConfig = SpeechDetectorConfig(),
) {
    private companion object {
        /** Tolerance for float accumulation error when summing per-frame durations. */
        const val FLOAT_EPSILON = 1e-4f
    }

    var state: SpeechState = SpeechState.LISTENING
        private set
    private var trailingSilenceSeconds = 0f
    private var noiseFloorEstimate = 0f
    private var listeningFrameCount = 0

    private val effectiveStartThreshold: Float
        get() =
            if (listeningFrameCount < MIN_CALIBRATION_FRAMES) {
                config.startThreshold
            } else {
                maxOf(config.startThreshold, noiseFloorEstimate * NOISE_FLOOR_START_MARGIN)
            }

    private val effectiveStopThreshold: Float
        get() =
            if (listeningFrameCount < MIN_CALIBRATION_FRAMES) {
                config.stopThreshold
            } else {
                minOf(
                    maxOf(config.stopThreshold, noiseFloorEstimate * NOISE_FLOOR_STOP_MARGIN),
                    effectiveStartThreshold * STOP_THRESHOLD_HEADROOM,
                )
            }

    fun reset() {
        state = SpeechState.LISTENING
        trailingSilenceSeconds = 0f
        noiseFloorEstimate = 0f
        listeningFrameCount = 0
    }

    /**
     * Feeds one frame of audio into the detector.
     *
     * @param frame block of PCM-16 samples to analyze
     * @param frameDurationSeconds duration this frame represents, in seconds
     * @param silenceThresholdSeconds trailing silence duration (after speech has started)
     *   that triggers [SpeechTransition.AutoStop]
     */
    fun onFrame(
        frame: ShortArray,
        frameDurationSeconds: Float,
        silenceThresholdSeconds: Float,
    ): SpeechTransition {
        val amplitude = peakAmplitude(frame)
        return when (state) {
            SpeechState.LISTENING ->
                if (amplitude > effectiveStartThreshold) {
                    state = SpeechState.SPEAKING
                    SpeechTransition.StartedSpeaking
                } else {
                    noiseFloorEstimate =
                        if (listeningFrameCount == 0) {
                            amplitude
                        } else {
                            noiseFloorEstimate + NOISE_FLOOR_ALPHA * (amplitude - noiseFloorEstimate)
                        }
                    listeningFrameCount++
                    SpeechTransition.None
                }

            SpeechState.SPEAKING ->
                if (amplitude <= effectiveStopThreshold) {
                    state = SpeechState.TRAILING_SILENCE
                    trailingSilenceSeconds = frameDurationSeconds
                    SpeechTransition.SilenceProgress(
                        (silenceThresholdSeconds - trailingSilenceSeconds).coerceAtLeast(0f),
                    )
                } else {
                    SpeechTransition.None
                }

            SpeechState.TRAILING_SILENCE ->
                if (amplitude > effectiveStopThreshold) {
                    state = SpeechState.SPEAKING
                    trailingSilenceSeconds = 0f
                    SpeechTransition.ResumedSpeaking
                } else {
                    trailingSilenceSeconds += frameDurationSeconds
                    if (trailingSilenceSeconds >= silenceThresholdSeconds - FLOAT_EPSILON) {
                        SpeechTransition.AutoStop
                    } else {
                        SpeechTransition.SilenceProgress(
                            (silenceThresholdSeconds - trailingSilenceSeconds).coerceAtLeast(0f),
                        )
                    }
                }
        }
    }
}
