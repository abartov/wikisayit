package wiki.asaf.wikisayit.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun toneFrame(amplitude: Float): ShortArray = shortArrayOf((amplitude * 32768f).toInt().toShort())

private fun silenceFrame(): ShortArray = shortArrayOf(0)

class SpeechEndpointDetectorTest {
    private val detector = SpeechEndpointDetector(SpeechDetectorConfig(startThreshold = 0.08f, stopThreshold = 0.04f))

    @Test
    fun `stays listening below start threshold`() {
        val transition =
            detector.onFrame(
                toneFrame(0.05f),
                frameDurationSeconds = 0.02f,
                silenceThresholdSeconds = 1.5f,
            )
        assertEquals(SpeechTransition.None, transition)
        assertEquals(SpeechState.LISTENING, detector.state)
    }

    @Test
    fun `crosses start threshold and reports started speaking`() {
        val transition = detector.onFrame(toneFrame(0.5f), frameDurationSeconds = 0.02f, silenceThresholdSeconds = 1.5f)
        assertEquals(SpeechTransition.StartedSpeaking, transition)
        assertEquals(SpeechState.SPEAKING, detector.state)
    }

    @Test
    fun `does not auto-stop while still listening, even after many silent frames`() {
        repeat(500) {
            val transition =
                detector.onFrame(
                    silenceFrame(),
                    frameDurationSeconds = 0.02f,
                    silenceThresholdSeconds = 1.5f,
                )
            assertEquals(SpeechTransition.None, transition)
        }
        assertEquals(SpeechState.LISTENING, detector.state)
    }

    @Test
    fun `auto-stops after silenceThresholdSeconds of trailing silence following speech`() {
        detector.onFrame(toneFrame(0.5f), frameDurationSeconds = 0.02f, silenceThresholdSeconds = 0.1f)

        // 4 frames of 0.02s = 0.08s, short of the 0.1s threshold.
        repeat(4) {
            val transition =
                detector.onFrame(
                    silenceFrame(),
                    frameDurationSeconds = 0.02f,
                    silenceThresholdSeconds = 0.1f,
                )
            assertTrue(transition is SpeechTransition.SilenceProgress)
        }
        assertEquals(SpeechState.TRAILING_SILENCE, detector.state)

        val autoStop = detector.onFrame(silenceFrame(), frameDurationSeconds = 0.02f, silenceThresholdSeconds = 0.1f)
        assertEquals(SpeechTransition.AutoStop, autoStop)
    }

    @Test
    fun `resumed speech during trailing silence resets the countdown`() {
        detector.onFrame(toneFrame(0.5f), frameDurationSeconds = 0.02f, silenceThresholdSeconds = 0.1f)
        detector.onFrame(silenceFrame(), frameDurationSeconds = 0.08f, silenceThresholdSeconds = 0.1f)

        val resumed = detector.onFrame(toneFrame(0.5f), frameDurationSeconds = 0.02f, silenceThresholdSeconds = 0.1f)
        assertEquals(SpeechTransition.ResumedSpeaking, resumed)
        assertEquals(SpeechState.SPEAKING, detector.state)

        // Silence budget should have reset, not immediately auto-stop on the next silent frame.
        val next = detector.onFrame(silenceFrame(), frameDurationSeconds = 0.08f, silenceThresholdSeconds = 0.1f)
        assertTrue(next is SpeechTransition.SilenceProgress)
    }

    @Test
    fun `reset returns detector to listening state`() {
        detector.onFrame(toneFrame(0.5f), frameDurationSeconds = 0.02f, silenceThresholdSeconds = 1.5f)
        detector.reset()
        assertEquals(SpeechState.LISTENING, detector.state)
    }
}
