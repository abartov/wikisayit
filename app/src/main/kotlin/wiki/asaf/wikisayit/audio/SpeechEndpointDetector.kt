package wiki.asaf.wikisayit.audio

enum class SpeechState { LISTENING, SPEAKING, TRAILING_SILENCE }

data class SpeechDetectorConfig(
    /** Frame RMS (0f..1f) that counts as speech while [SpeechState.LISTENING], in a room quiet
     * enough that the measured noise floor doesn't raise it. Roughly -38 dBFS: soft speech at
     * arm's length, well under a normal speaking voice, but clear of a quiet room's background.
     * Frames must hold this for [ONSET_CONFIRMATION_SECONDS] before speech is declared, which is
     * what makes a threshold this sensitive safe. */
    val startThresholdRms: Float = 0.012f,
    /** Lowest the start threshold may go once the noise floor is calibrated and turns out to be
     * well below [startThresholdRms] (~-44 dBFS). Mic gain varies a lot between devices, and on a
     * low-gain one a softly spoken word can peak under [startThresholdRms] while still standing
     * 20 dB clear of a quiet room — keying the bar to the measured floor down to here is what
     * lets such a word start the take instead of leaving it listening indefinitely. */
    val quietRoomStartThresholdRms: Float = 0.006f,
    /** Frame RMS (0f..1f) below which a frame counts as silence once speech has started; half the
     * start threshold, so a take that has begun needs a real 6 dB drop to look silent. */
    val stopThresholdRms: Float = 0.006f,
)

/** Frames at the very start of a take are used purely as a noise sample, taking the *minimum*
 * level seen: over this short a window a minimum is a better floor estimate than an average,
 * which any single transient would drag upward. The static thresholds apply until it elapses. */
private const val CALIBRATION_SECONDS = 0.2f

/** After calibration the floor keeps tracking, but asymmetrically: it falls quickly toward a
 * quieter room and rises only glacially, so speech the detector hasn't recognized yet cannot
 * drag the floor — and with it the start threshold — up ahead of itself. That ratchet was why a
 * soft or gradual onset could leave the app listening straight through a spoken word. */
private const val NOISE_FLOOR_FALL_ALPHA = 0.25f
private const val NOISE_FLOOR_RISE_ALPHA = 0.01f

/** Upward tracking stops entirely this far into a take: by then the room has been sampled, and
 * anything louder arriving later is far more likely to be the user than the room. */
private const val NOISE_FLOOR_RISE_WINDOW_SECONDS = 1f

/** How far above the measured noise floor the effective start/stop thresholds sit, so a room with
 * a nonzero background level doesn't get stuck never seeing the level drop back below a threshold
 * set for near-total silence — and, in a quiet room, how far above the floor speech has to be
 * for the start threshold to sit below [SpeechDetectorConfig.startThresholdRms]. */
private const val NOISE_FLOOR_STOP_MARGIN = 2.0f
private const val NOISE_FLOOR_START_MARGIN = 3.5f

/** Ceiling on how far ambient noise may push the start threshold above
 * [SpeechDetectorConfig.startThresholdRms]. Past this the room is loud enough that no threshold
 * separates it from speech reliably, and raising the bar further only guarantees missed onsets —
 * better to start (and let [RecordingEngine] surface the manual-mode hint) than to never start. */
private const val MAX_ADAPTIVE_START_GAIN = 6f

/** Effective stop threshold is capped to this fraction of the effective start threshold, keeping
 * a meaningful gap between "is speaking" and "is silent" even in a noisy room. */
private const val STOP_THRESHOLD_HEADROOM = 0.75f

/** How long the level must stay above the start threshold before speech is declared. Long enough
 * to ignore a click or a knock, short enough that no audible part of the word is at risk: every
 * frame is buffered from the moment the take opens, and [cropSilence] trims the lead-in
 * afterwards, so confirming onset late costs nothing but the UI switching a few frames later. */
private const val ONSET_CONFIRMATION_SECONDS = 0.04f

/** Fraction of the start threshold a LISTENING frame must reach to count toward
 * [SpeechEndpointDetector.unconfirmedEnergySeconds]. */
private const val NEAR_MISS_RATIO = 0.5f

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
 * (~/dev/LinguaRecorder/src/RecordingProcessor.js) and since adapted for noisy rooms.
 *
 * While [SpeechState.LISTENING], no auto-stop timer runs — a word screen with no speech at
 * all is left to the user to abandon, per the product spec (auto-stop only follows speech).
 *
 * The LISTENING frames before speech onset double as an ambient noise sample feeding a running
 * noise floor estimate. In a quiet room that lowers the start threshold toward
 * [SpeechDetectorConfig.quietRoomStartThresholdRms], so soft speech on a low-gain mic still
 * registers; in a room with a nonzero background level it raises the effective start/stop
 * thresholds above [SpeechDetectorConfig]'s static defaults — otherwise
 * the level can sit above [SpeechDetectorConfig.stopThresholdRms] indefinitely after the user
 * stops talking, and auto-stop never triggers. Everything about how that estimate moves is
 * deliberately biased against the estimate growing: it starts as a minimum, then falls fast and
 * rises slowly, stops rising a second in, is capped in how far it can lift the start threshold,
 * and freezes outright once speech starts.
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

    /**
     * Seconds of LISTENING audio carrying energy within [NEAR_MISS_RATIO] of the start threshold
     * that never confirmed as speech. Something was audible and the detector didn't call it
     * speech — the signal [RecordingEngine] uses to offer manual mode rather than leave the user
     * watching a take that will never start.
     */
    var unconfirmedEnergySeconds = 0f
        private set

    /**
     * Ambient noise floor in peak (not RMS) terms, for callers working in the sample-magnitude
     * units [cropSilence] trims by. 0f until the first frame is seen.
     */
    var noiseFloorPeak = 0f
        private set

    private var noiseFloorRms = 0f
    private var seenAnyFrame = false
    private var listeningSeconds = 0f
    private var onsetRunSeconds = 0f
    private var trailingSilenceSeconds = 0f

    /** The floor is only trusted once it has had a moment to settle; before that a single frame
     * is all it has seen, and the static thresholds are the safer bet. */
    private val floorIsCalibrated: Boolean
        get() = seenAnyFrame && listeningSeconds >= CALIBRATION_SECONDS

    private val effectiveStartThreshold: Float
        get() =
            if (!floorIsCalibrated) {
                config.startThresholdRms
            } else {
                maxOf(config.quietRoomStartThresholdRms, noiseFloorRms * NOISE_FLOOR_START_MARGIN)
                    .coerceAtMost(config.startThresholdRms * MAX_ADAPTIVE_START_GAIN)
            }

    private val effectiveStopThreshold: Float
        get() =
            if (!floorIsCalibrated) {
                config.stopThresholdRms
            } else {
                minOf(
                    maxOf(config.stopThresholdRms, noiseFloorRms * NOISE_FLOOR_STOP_MARGIN),
                    effectiveStartThreshold * STOP_THRESHOLD_HEADROOM,
                )
            }

    fun reset() {
        state = SpeechState.LISTENING
        unconfirmedEnergySeconds = 0f
        noiseFloorPeak = 0f
        noiseFloorRms = 0f
        seenAnyFrame = false
        listeningSeconds = 0f
        onsetRunSeconds = 0f
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
        val level = frameLevel(frame)
        return when (state) {
            SpeechState.LISTENING -> onListeningFrame(level, frameDurationSeconds)

            SpeechState.SPEAKING ->
                if (level.rms <= effectiveStopThreshold) {
                    state = SpeechState.TRAILING_SILENCE
                    trailingSilenceSeconds = frameDurationSeconds
                    SpeechTransition.SilenceProgress(
                        (silenceThresholdSeconds - trailingSilenceSeconds).coerceAtLeast(0f),
                    )
                } else {
                    SpeechTransition.None
                }

            SpeechState.TRAILING_SILENCE ->
                if (level.rms > effectiveStopThreshold) {
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

    /** Handles a frame while still listening for speech onset; no silence countdown runs here, so
     * the only transition this can produce is [SpeechTransition.StartedSpeaking]. */
    private fun onListeningFrame(
        level: FrameLevel,
        frameDurationSeconds: Float,
    ): SpeechTransition {
        // Read before updating the floor: this frame's own energy must not be allowed to move the
        // bar it is being judged against.
        val startThreshold = effectiveStartThreshold
        if (level.rms > startThreshold) {
            onsetRunSeconds += frameDurationSeconds
            if (onsetRunSeconds >= ONSET_CONFIRMATION_SECONDS - FLOAT_EPSILON) {
                state = SpeechState.SPEAKING
                trailingSilenceSeconds = 0f
                return SpeechTransition.StartedSpeaking
            }
        } else {
            onsetRunSeconds = 0f
        }
        if (level.rms > startThreshold * NEAR_MISS_RATIO) {
            unconfirmedEnergySeconds += frameDurationSeconds
        }
        updateNoiseFloor(level)
        listeningSeconds += frameDurationSeconds
        return SpeechTransition.None
    }

    private fun updateNoiseFloor(level: FrameLevel) {
        if (!seenAnyFrame) {
            seenAnyFrame = true
            noiseFloorRms = level.rms
            noiseFloorPeak = level.peak
            return
        }
        if (listeningSeconds < CALIBRATION_SECONDS) {
            // Pure minimum tracking while calibrating.
            noiseFloorRms = minOf(noiseFloorRms, level.rms)
            noiseFloorPeak = minOf(noiseFloorPeak, level.peak)
            return
        }
        val risingAllowed = listeningSeconds < NOISE_FLOOR_RISE_WINDOW_SECONDS
        noiseFloorRms = track(noiseFloorRms, level.rms, risingAllowed)
        noiseFloorPeak = track(noiseFloorPeak, level.peak, risingAllowed)
    }

    private fun track(
        estimate: Float,
        observed: Float,
        risingAllowed: Boolean,
    ): Float =
        when {
            observed < estimate -> estimate + NOISE_FLOOR_FALL_ALPHA * (observed - estimate)
            risingAllowed -> estimate + NOISE_FLOOR_RISE_ALPHA * (observed - estimate)
            else -> estimate
        }
}
