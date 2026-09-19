package wiki.asaf.wikisayit.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import java.io.File

/**
 * Records one word at a time: captures live audio, tracks speech onset/trailing silence to
 * know when to stop, then crops, pads, and encodes the result.
 *
 * To abandon a recording in progress (Skip/Stop/Redo), cancel the coroutine collecting
 * [recordWord] — [AudioSource.frames] releases the microphone on cancellation, and no
 * [Event.Finished] is emitted.
 */
class RecordingEngine(
    private val audioSource: AudioSource,
    private val encoder: AudioEncoder = OggVorbisEncoder(),
    private val speechConfig: SpeechDetectorConfig = SpeechDetectorConfig(),
    private val cropThreshold: Float = DEFAULT_CROP_THRESHOLD,
    private val paddingSeconds: Float = DEFAULT_PADDING_SECONDS,
    private val minDurationSeconds: Float = 0.15f,
) {
    sealed interface Event {
        data object Listening : Event

        data object Speaking : Event

        data class Silence(val remainingSeconds: Float) : Event

        /** Emitted once per take when automatic endpointing looks like it isn't going to work in
         * this environment, either because [DIFFICULTY_WARNING_SECONDS] passed since speech onset
         * with no trailing silence ever detected (ambient noise sitting above the stop threshold,
         * so auto-stop may never trigger), or because that long passed with audible energy that
         * never confirmed as speech (so the take is stuck listening and will never start).
         * Recording keeps going; this only hints that manual mode might work better here. */
        data object DifficultyDetecting : Event

        data class Finished(val file: File, val durationSeconds: Float) : Event

        /** The cropped recording was shorter than [minDurationSeconds]; nothing was written. */
        data object TooShort : Event
    }

    private companion object {
        const val DIFFICULTY_WARNING_SECONDS = 6f

        /** How much near-threshold-but-unconfirmed audio has to accumulate before a take that is
         * still listening counts as struggling rather than as a user who simply hasn't spoken yet
         * — a silent room should never raise the hint. */
        const val UNCONFIRMED_ENERGY_WARNING_SECONDS = 0.5f

        /** The adaptive crop threshold is never allowed above this fraction of the take's own peak,
         * so a take quiet enough to sit near the noise floor is still cropped rather than erased
         * (an over-eager threshold trims every sample and the take is reported [Event.TooShort]). */
        const val MAX_CROP_FRACTION_OF_PEAK = 0.2f

        /** Headroom over the measured ambient peak for the adaptive crop threshold. */
        const val CROP_NOISE_FLOOR_MARGIN = 1.5f

        /** How far a take's peak must stand out from the ambient noise before the crop threshold
         * is relaxed on its behalf. Below this the take is indistinguishable from the room — a
         * rustle or a knock rather than a quiet word — and cropping it away to [Event.TooShort]
         * (so the word is simply re-recorded) beats handing the user a take of nothing. */
        const val CROP_RELAXATION_MIN_SIGNAL = 3f
    }

    /**
     * @param outputFile where the encoded Ogg Vorbis file is written, on [Event.Finished]
     * @param silenceThresholdSeconds trailing silence duration (after speech starts) that ends the recording
     */
    fun recordWord(
        outputFile: File,
        silenceThresholdSeconds: Float,
    ): Flow<Event> =
        flow {
            val detector = SpeechEndpointDetector(speechConfig)
            val blocks = mutableListOf<ShortArray>()
            emit(Event.Listening)

            var secondsSinceSpeechStarted = 0f
            var listeningSeconds = 0f
            var sawSilenceProgress = false
            var difficultyWarningEmitted = false

            audioSource.frames()
                .transformWhile { frame ->
                    blocks.add(frame)
                    val frameDurationSeconds = frame.size.toFloat() / audioSource.sampleRate
                    when (val transition = detector.onFrame(frame, frameDurationSeconds, silenceThresholdSeconds)) {
                        SpeechTransition.StartedSpeaking, SpeechTransition.ResumedSpeaking -> emit(Event.Speaking)
                        is SpeechTransition.SilenceProgress -> {
                            sawSilenceProgress = true
                            emit(Event.Silence(transition.remainingSeconds))
                        }
                        SpeechTransition.AutoStop -> return@transformWhile false
                        SpeechTransition.None -> Unit
                    }
                    if (!difficultyWarningEmitted) {
                        val struggling =
                            if (detector.state == SpeechState.LISTENING) {
                                listeningSeconds += frameDurationSeconds
                                listeningSeconds >= DIFFICULTY_WARNING_SECONDS &&
                                    detector.unconfirmedEnergySeconds >= UNCONFIRMED_ENERGY_WARNING_SECONDS
                            } else if (!sawSilenceProgress) {
                                secondsSinceSpeechStarted += frameDurationSeconds
                                secondsSinceSpeechStarted >= DIFFICULTY_WARNING_SECONDS
                            } else {
                                false
                            }
                        if (struggling) {
                            difficultyWarningEmitted = true
                            emit(Event.DifficultyDetecting)
                        }
                    }
                    true
                }.collect { emit(it) }

            finish(blocks, outputFile, detector.noiseFloorPeak)
        }

    /**
     * Captures audio with no speech-onset/silence-based auto-stop: recording continues until
     * [stopRequested] reads true (polled once per captured frame) or the audio source completes
     * on its own. Used by manual recording mode (s-3fe), where the user drives start/stop instead
     * of [SpeechEndpointDetector].
     */
    fun recordWordManual(
        outputFile: File,
        stopRequested: StateFlow<Boolean>,
    ): Flow<Event> =
        flow {
            val blocks = mutableListOf<ShortArray>()
            emit(Event.Listening)

            audioSource.frames()
                .transformWhile { frame ->
                    val isFirstFrame = blocks.isEmpty()
                    blocks.add(frame)
                    if (isFirstFrame) emit(Event.Speaking)
                    !stopRequested.value
                }.collect { emit(it) }

            finish(blocks, outputFile)
        }

    private suspend fun FlowCollector<Event>.finish(
        blocks: List<ShortArray>,
        outputFile: File,
        noiseFloorPeak: Float = 0f,
    ) {
        val samples = flattenBlocks(blocks)
        val cropped = cropSilence(samples, effectiveCropThreshold(samples, noiseFloorPeak))
        if (cropped.size < (minDurationSeconds * audioSource.sampleRate).toInt()) {
            emit(Event.TooShort)
            return
        }

        val padded = addPadding(cropped, audioSource.sampleRate, paddingSeconds)
        encoder.encode(padded, audioSource.sampleRate, outputFile)
        emit(Event.Finished(outputFile, cropped.size.toFloat() / audioSource.sampleRate))
    }

    /**
     * Crop threshold for one finished take: [cropThreshold] raised to clear the ambient noise the
     * detector measured (cropping at a level the room never goes below leaves the take untrimmed),
     * then held under a fraction of the take's own peak. That cap matters now that speech onset is
     * detected well below the static threshold: without it a quiet take recorded in a quiet room
     * — peak below [cropThreshold] — would be cropped away entirely and reported [Event.TooShort].
     */
    private fun effectiveCropThreshold(
        samples: ShortArray,
        noiseFloorPeak: Float,
    ): Float {
        val takePeak = frameLevel(samples).peak
        if (takePeak <= 0f) return cropThreshold
        val floorBased = maxOf(cropThreshold, noiseFloorPeak * CROP_NOISE_FLOOR_MARGIN)
        if (takePeak < noiseFloorPeak * CROP_RELAXATION_MIN_SIGNAL) return floorBased
        return floorBased.coerceAtMost(takePeak * MAX_CROP_FRACTION_OF_PEAK)
    }

    private fun flattenBlocks(blocks: List<ShortArray>): ShortArray {
        val total = blocks.sumOf { it.size }
        val result = ShortArray(total)
        var offset = 0
        for (block in blocks) {
            block.copyInto(result, offset)
            offset += block.size
        }
        return result
    }
}
