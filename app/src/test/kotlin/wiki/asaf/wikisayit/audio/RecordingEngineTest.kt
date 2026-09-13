package wiki.asaf.wikisayit.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private fun frame(amplitude: Float): ShortArray = ShortArray(10) { (amplitude * 32768f).toInt().toShort() }

private class ScriptedAudioSource(
    override val sampleRate: Int,
    private val script: List<ShortArray>,
) : AudioSource {
    override fun frames(): Flow<ShortArray> = script.asFlow()
}

private class RecordingAudioEncoder : AudioEncoder {
    var lastSamples: ShortArray? = null

    override fun encode(
        samples: ShortArray,
        sampleRate: Int,
        output: File,
    ) {
        lastSamples = samples
        output.writeBytes(ByteArray(0))
    }
}

class RecordingEngineTest {
    // 100Hz sample rate, 10 samples/frame => 0.1s per frame. Loud frames (2, 3) bracketed by
    // silence: 0.1s to listen through, then 0.3s of trailing silence to trigger auto-stop.
    private val script =
        listOf(
            // listening, below start threshold
            frame(0f),
            // speech starts
            frame(0.5f),
            // still speaking
            frame(0.5f),
            // trailing silence 0.1s
            frame(0f),
            // trailing silence 0.2s
            frame(0f),
            // trailing silence 0.3s -> auto-stop
            frame(0f),
            // should never be consumed: collection stops at auto-stop
            frame(0.5f),
        )

    @Test
    fun `emits listening, speaking, silence progress, then finished`() =
        runTest {
            val encoder = RecordingAudioEncoder()
            val engine =
                RecordingEngine(
                    audioSource = ScriptedAudioSource(sampleRate = 100, script = script),
                    encoder = encoder,
                    minDurationSeconds = 0.15f,
                )
            val output = File.createTempFile("recording-engine-test", ".ogg")
            output.deleteOnExit()

            val events = engine.recordWord(output, silenceThresholdSeconds = 0.3f).toList()

            assertEquals(RecordingEngine.Event.Listening, events.first())
            assertTrue(events.contains(RecordingEngine.Event.Speaking))
            assertTrue(events.count { it is RecordingEngine.Event.Silence } >= 2)
            val finished = events.last()
            assertTrue(finished is RecordingEngine.Event.Finished)
            finished as RecordingEngine.Event.Finished
            assertEquals(output, finished.file)
            // Cropped to just the two loud frames (20 samples @ 100Hz = 0.2s), pre-padding.
            assertEquals(0.2f, finished.durationSeconds, 0.001f)

            // Encoder receives the cropped audio plus 0.2s (20 samples @ 100Hz) padding on each side.
            assertEquals(60, encoder.lastSamples?.size)
        }

    @Test
    fun `reports too short instead of encoding when cropped audio is below the minimum`() =
        runTest {
            val encoder = RecordingAudioEncoder()
            val engine =
                RecordingEngine(
                    audioSource = ScriptedAudioSource(sampleRate = 100, script = script),
                    encoder = encoder,
                    minDurationSeconds = 1f,
                )
            val output = File.createTempFile("recording-engine-test-short", ".ogg")
            output.deleteOnExit()

            val events = engine.recordWord(output, silenceThresholdSeconds = 0.3f).toList()

            assertEquals(RecordingEngine.Event.TooShort, events.last())
            assertEquals(null, encoder.lastSamples)
        }
}
