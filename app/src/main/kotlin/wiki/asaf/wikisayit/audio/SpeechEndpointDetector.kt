package wiki.asaf.wikisayit.audio

enum class SpeechState { LISTENING, SPEAKING, TRAILING_SILENCE }

data class SpeechDetectorConfig(
    /** Peak amplitude (0f..1f) that counts as the start of speech while [SpeechState.LISTENING]. */
    val startThreshold: Float = 0.08f,
    /** Peak amplitude (0f..1f) below which a frame counts as silence once speech has started. */
    val stopThreshold: Float = 0.04f,
)

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

    fun reset() {
        state = SpeechState.LISTENING
        trailingSilenceSeconds = 0f
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
                if (amplitude > config.startThreshold) {
                    state = SpeechState.SPEAKING
                    SpeechTransition.StartedSpeaking
                } else {
                    SpeechTransition.None
                }

            SpeechState.SPEAKING ->
                if (amplitude <= config.stopThreshold) {
                    state = SpeechState.TRAILING_SILENCE
                    trailingSilenceSeconds = frameDurationSeconds
                    SpeechTransition.SilenceProgress(
                        (silenceThresholdSeconds - trailingSilenceSeconds).coerceAtLeast(0f),
                    )
                } else {
                    SpeechTransition.None
                }

            SpeechState.TRAILING_SILENCE ->
                if (amplitude > config.stopThreshold) {
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
