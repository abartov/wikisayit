package wiki.asaf.wikisayit.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Mono PCM-16 audio frame source: a cold stream of sample blocks captured while collected. */
interface AudioSource {
    val sampleRate: Int

    /**
     * Starts capturing on collection and stops when the collecting coroutine is cancelled.
     * Each emitted [ShortArray] is a fresh block of consecutive samples.
     */
    fun frames(): Flow<ShortArray>
}

class MicrophoneUnavailableException(message: String) : Exception(message)

/** Frame size for reading from [AudioRecord], independent of the device's minimum buffer size
 * (see [AndroidAudioSource] for why). 20ms is fine-grained enough for [SpeechEndpointDetector]
 * to track speech onset/trailing silence responsively without excessive per-frame overhead. */
private const val FRAME_DURATION_SECONDS = 0.02f

/**
 * Captures raw mono PCM-16 audio from the device microphone via [AudioRecord].
 *
 * Requires the caller to already hold [android.Manifest.permission.RECORD_AUDIO] —
 * this class does not request permissions itself.
 */
class AndroidAudioSource(
    override val sampleRate: Int = 44_100,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioSource {
    @SuppressLint("MissingPermission")
    override fun frames(): Flow<ShortArray> =
        flow {
            val minBufferSize =
                AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw MicrophoneUnavailableException("Unsupported sample rate: $sampleRate")
            }
            // The internal ring buffer can be generous for headroom, but reads below are always
            // in fixed, small frames — AudioRecord.getMinBufferSize() varies a lot by device
            // (some report buffers several times larger than others for the same format), and
            // this class used to read exactly one buffer's worth per frame. That tied speech
            // onset/silence-countdown granularity to that device-specific size — on a device
            // with a large minimum buffer, a single frame could span longer than a whole short
            // word, so the detector saw it as one coarse blob and either mis-timed auto-stop or
            // made the crop step trim a genuine take down to nothing (reported as TooShort).
            val bufferSize = minBufferSize * 2
            val record =
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                throw MicrophoneUnavailableException("AudioRecord failed to initialize")
            }
            try {
                record.startRecording()
                val frameSamples = (sampleRate * FRAME_DURATION_SECONDS).toInt().coerceAtLeast(1)
                val chunk = ShortArray(frameSamples)
                while (true) {
                    val read = record.read(chunk, 0, chunk.size)
                    if (read > 0) {
                        emit(chunk.copyOf(read))
                    } else if (read < 0) {
                        throw MicrophoneUnavailableException("AudioRecord.read() failed with error code $read")
                    }
                }
            } finally {
                record.stop()
                record.release()
            }
        }.flowOn(ioDispatcher)
}
