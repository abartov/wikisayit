package wiki.asaf.wikisayit.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    @Test
    fun `emits difficulty detecting once after six seconds of speech with no trailing silence`() =
        runTest {
            val encoder = RecordingAudioEncoder()
            // 100Hz sample rate, 10 samples/frame => 0.1s per frame; 62 loud frames span 6.2s of
            // continuous "speech" with no trailing silence anywhere in the script.
            val noSilenceScript = listOf(frame(0f)) + List(61) { frame(0.5f) }
            val engine =
                RecordingEngine(
                    audioSource = ScriptedAudioSource(sampleRate = 100, script = noSilenceScript),
                    encoder = encoder,
                    minDurationSeconds = 0.15f,
                )
            val output = File.createTempFile("recording-engine-difficulty-test", ".ogg")
            output.deleteOnExit()

            val events = engine.recordWord(output, silenceThresholdSeconds = 0.3f).toList()

            assertEquals(1, events.count { it is RecordingEngine.Event.DifficultyDetecting })
            assertTrue(events.none { it is RecordingEngine.Event.Silence })
            assertTrue(events.last() is RecordingEngine.Event.Finished)
        }

    @Test
    fun `emits difficulty detecting when a take sits listening through audible near-threshold energy`() =
        runTest {
            val encoder = RecordingAudioEncoder()
            // A room at 0.008, then audio at 0.02 — above the noise floor, audible, but not the
            // 3.5x over it that confirms speech. This is the residual case where the app shows
            // "listening" indefinitely, and the one the manual-mode hint exists for.
            val stuckListeningScript = List(5) { frame(0.008f) } + List(60) { frame(0.02f) }
            val engine =
                RecordingEngine(
                    audioSource = ScriptedAudioSource(sampleRate = 100, script = stuckListeningScript),
                    encoder = encoder,
                    minDurationSeconds = 0.15f,
                )
            val output = File.createTempFile("recording-engine-stuck-listening-test", ".ogg")
            output.deleteOnExit()

            val events = engine.recordWord(output, silenceThresholdSeconds = 0.3f).toList()

            assertEquals(1, events.count { it is RecordingEngine.Event.DifficultyDetecting })
            assertTrue(events.none { it is RecordingEngine.Event.Speaking })
        }

    @Test
    fun `stays quiet about a take that is simply silent`() =
        runTest {
            val encoder = RecordingAudioEncoder()
            val engine =
                RecordingEngine(
                    audioSource = ScriptedAudioSource(sampleRate = 100, script = List(120) { frame(0f) }),
                    encoder = encoder,
                    minDurationSeconds = 0.15f,
                )
            val output = File.createTempFile("recording-engine-silent-test", ".ogg")
            output.deleteOnExit()

            val events = engine.recordWord(output, silenceThresholdSeconds = 0.3f).toList()

            assertTrue(events.none { it is RecordingEngine.Event.DifficultyDetecting })
        }

    @Test
    fun `a quiet take is cropped against its own peak rather than erased`() =
        runTest {
            val encoder = RecordingAudioEncoder()
            // Speech below the static crop threshold of 0.04: cropping at a fixed threshold would
            // trim every sample and report the take too short.
            val quietScript =
                listOf(frame(0f)) + List(3) { frame(0.03f) } + List(3) { frame(0f) }
            val engine =
                RecordingEngine(
                    audioSource = ScriptedAudioSource(sampleRate = 100, script = quietScript),
                    encoder = encoder,
                    minDurationSeconds = 0.15f,
                )
            val output = File.createTempFile("recording-engine-quiet-test", ".ogg")
            output.deleteOnExit()

            val events = engine.recordWord(output, silenceThresholdSeconds = 0.3f).toList()

            assertTrue(events.contains(RecordingEngine.Event.Speaking))
            val finished = events.last()
            assertTrue(finished is RecordingEngine.Event.Finished)
            finished as RecordingEngine.Event.Finished
            assertEquals(0.3f, finished.durationSeconds, 0.001f)
        }

    @Test
    fun `manual recording ignores speech-silence auto-stop and captures until the source completes`() =
        runTest {
            val encoder = RecordingAudioEncoder()
            val engine =
                RecordingEngine(
                    audioSource = ScriptedAudioSource(sampleRate = 100, script = script),
                    encoder = encoder,
                    minDurationSeconds = 0.15f,
                )
            val output = File.createTempFile("recording-engine-manual-test", ".ogg")
            output.deleteOnExit()

            // Never set true: the flow should finish only once ScriptedAudioSource's finite script
            // is exhausted, including the trailing loud frame that auto-stop mode never reaches.
            val stopRequested = MutableStateFlow(false)
            val events = engine.recordWordManual(output, stopRequested).toList()

            assertEquals(RecordingEngine.Event.Listening, events.first())
            assertTrue(events.contains(RecordingEngine.Event.Speaking))
            assertTrue(events.none { it is RecordingEngine.Event.Silence })
            val finished = events.last()
            assertTrue(finished is RecordingEngine.Event.Finished)
            finished as RecordingEngine.Event.Finished
            // Cropped span covers the first through last loud frame (indices 1..6), including the
            // silent gap between them: 6 frames @ 100Hz = 0.6s.
            assertEquals(0.6f, finished.durationSeconds, 0.001f)
        }

    @Test
    fun `manual recording stopped immediately reports too short rather than encoding near-empty audio`() =
        runTest {
            val encoder = RecordingAudioEncoder()
            val engine =
                RecordingEngine(
                    audioSource = ScriptedAudioSource(sampleRate = 100, script = script),
                    encoder = encoder,
                    minDurationSeconds = 0.15f,
                )
            val output = File.createTempFile("recording-engine-manual-stop-test", ".ogg")
            output.deleteOnExit()

            val stopRequested = MutableStateFlow(true)
            val events = engine.recordWordManual(output, stopRequested).toList()

            assertEquals(RecordingEngine.Event.TooShort, events.last())
            assertEquals(null, encoder.lastSamples)
        }
}
