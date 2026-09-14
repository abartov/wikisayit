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

        data class Finished(val file: File, val durationSeconds: Float) : Event

        /** The cropped recording was shorter than [minDurationSeconds]; nothing was written. */
        data object TooShort : Event
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

            audioSource.frames()
                .transformWhile { frame ->
                    blocks.add(frame)
                    val frameDurationSeconds = frame.size.toFloat() / audioSource.sampleRate
                    when (val transition = detector.onFrame(frame, frameDurationSeconds, silenceThresholdSeconds)) {
                        SpeechTransition.StartedSpeaking, SpeechTransition.ResumedSpeaking -> emit(Event.Speaking)
                        is SpeechTransition.SilenceProgress -> emit(Event.Silence(transition.remainingSeconds))
                        SpeechTransition.AutoStop -> return@transformWhile false
                        SpeechTransition.None -> Unit
                    }
                    true
                }.collect { emit(it) }

            finish(blocks, outputFile)
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
    ) {
        val cropped = cropSilence(flattenBlocks(blocks), cropThreshold)
        if (cropped.size < (minDurationSeconds * audioSource.sampleRate).toInt()) {
            emit(Event.TooShort)
            return
        }

        val padded = addPadding(cropped, audioSource.sampleRate, paddingSeconds)
        encoder.encode(padded, audioSource.sampleRate, outputFile)
        emit(Event.Finished(outputFile, cropped.size.toFloat() / audioSource.sampleRate))
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
