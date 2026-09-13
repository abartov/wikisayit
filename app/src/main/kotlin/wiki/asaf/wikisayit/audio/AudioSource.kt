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
                val chunk = ShortArray(bufferSize / 2)
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
