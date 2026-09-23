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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/** A real microphone never delivers exact digital zeros for this long — even a quiet room carries
 * some self-noise — so a capture that does has been handed a silenced or not-yet-routed input.
 * Long enough to ride out the few hundred ms of zeros some devices emit while the input warms up. */
private const val DEAD_INPUT_SECONDS = 1f

/** How many times a dead capture is torn down and reopened before its silence is just passed on. */
private const val MAX_DEAD_INPUT_RESTARTS = 3

/** Held for the whole life of an [AudioRecord], process-wide: a take abandoned by cancellation
 * (Skip/Redo) releases its recorder only once its blocking [AudioRecord.read] returns, and the
 * next take is launched without waiting for that. Starting a second recorder while the first is
 * still open got the new one silenced on some devices, leaving auto mode listening indefinitely
 * to a mic that delivered nothing. */
private val microphoneLock = Mutex()

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
    override fun frames(): Flow<ShortArray> =
        flow {
            microphoneLock.withLock {
                val frameSamples = (sampleRate * FRAME_DURATION_SECONDS).toInt().coerceAtLeast(1)
                val deadInputSamples = (sampleRate * DEAD_INPUT_SECONDS).toInt()
                var restarts = 0
                while (true) {
                    val record = openRecord()
                    // Only a capture that has yet to deliver a single nonzero sample is watched;
                    // once the input has shown signs of life, silence is just silence.
                    var zeroSamples = 0
                    var watching = restarts < MAX_DEAD_INPUT_RESTARTS
                    try {
                        record.startRecording()
                        val chunk = ShortArray(frameSamples)
                        while (true) {
                            val read = record.read(chunk, 0, chunk.size)
                            if (read < 0) {
                                throw MicrophoneUnavailableException("AudioRecord.read() failed with error code $read")
                            }
                            if (read == 0) continue
                            val frame = chunk.copyOf(read)
                            if (watching) {
                                if (frame.all { it.toInt() == 0 }) {
                                    zeroSamples += read
                                } else {
                                    watching = false
                                }
                            }
                            // Emitted even while dead, so downstream timing keeps running.
                            emit(frame)
                            if (watching && zeroSamples >= deadInputSamples) break
                        }
                    } finally {
                        record.stop()
                        record.release()
                    }
                    restarts++
                }
            }
        }.flowOn(ioDispatcher)

    @SuppressLint("MissingPermission")
    private fun openRecord(): AudioRecord {
        val minBufferSize =
            AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            throw MicrophoneUnavailableException("Unsupported sample rate: $sampleRate")
        }
        // The internal ring buffer can be generous for headroom, but reads are always in fixed,
        // small frames — AudioRecord.getMinBufferSize() varies a lot by device (some report
        // buffers several times larger than others for the same format), and this class used to
        // read exactly one buffer's worth per frame. That tied speech onset/silence-countdown
        // granularity to that device-specific size — on a device with a large minimum buffer, a
        // single frame could span longer than a whole short word, so the detector saw it as one
        // coarse blob and either mis-timed auto-stop or made the crop step trim a genuine take
        // down to nothing (reported as TooShort).
        val record =
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2,
            )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw MicrophoneUnavailableException("AudioRecord failed to initialize")
        }
        return record
    }
}
