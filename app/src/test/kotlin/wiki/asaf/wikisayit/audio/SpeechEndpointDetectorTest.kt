package wiki.asaf.wikisayit.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A constant-valued frame, so its RMS and peak are both [amplitude]. */
private fun toneFrame(amplitude: Float): ShortArray = ShortArray(8) { (amplitude * 32768f).toInt().toShort() }

private fun silenceFrame(): ShortArray = ShortArray(8)

private const val FRAME = 0.02f

class SpeechEndpointDetectorTest {
    private val detector =
        SpeechEndpointDetector(SpeechDetectorConfig(startThresholdRms = 0.08f, stopThresholdRms = 0.04f))

    /** Onset takes two 20ms frames to confirm; this feeds enough of them to get there. */
    private fun speak(
        amplitude: Float,
        frames: Int = 3,
        silenceThresholdSeconds: Float = 1.5f,
    ): SpeechTransition {
        var last: SpeechTransition = SpeechTransition.None
        repeat(frames) {
            val transition = detector.onFrame(toneFrame(amplitude), FRAME, silenceThresholdSeconds)
            if (transition != SpeechTransition.None) last = transition
        }
        return last
    }

    @Test
    fun `stays listening below start threshold`() {
        val transition = detector.onFrame(toneFrame(0.05f), FRAME, silenceThresholdSeconds = 1.5f)
        assertEquals(SpeechTransition.None, transition)
        assertEquals(SpeechState.LISTENING, detector.state)
    }

    @Test
    fun `crosses start threshold and reports started speaking`() {
        assertEquals(SpeechTransition.StartedSpeaking, speak(0.5f))
        assertEquals(SpeechState.SPEAKING, detector.state)
    }

    @Test
    fun `a single loud frame is a click, not speech`() {
        val transition = detector.onFrame(toneFrame(0.9f), FRAME, silenceThresholdSeconds = 1.5f)
        assertEquals(SpeechTransition.None, transition)
        assertEquals(SpeechState.LISTENING, detector.state)
    }

    @Test
    fun `does not auto-stop while still listening, even after many silent frames`() {
        repeat(500) {
            val transition = detector.onFrame(silenceFrame(), FRAME, silenceThresholdSeconds = 1.5f)
            assertEquals(SpeechTransition.None, transition)
        }
        assertEquals(SpeechState.LISTENING, detector.state)
    }

    @Test
    fun `auto-stops after silenceThresholdSeconds of trailing silence following speech`() {
        speak(0.5f, silenceThresholdSeconds = 0.1f)

        // 4 frames of 0.02s = 0.08s, short of the 0.1s threshold.
        repeat(4) {
            val transition = detector.onFrame(silenceFrame(), FRAME, silenceThresholdSeconds = 0.1f)
            assertTrue(transition is SpeechTransition.SilenceProgress)
        }
        assertEquals(SpeechState.TRAILING_SILENCE, detector.state)

        val autoStop = detector.onFrame(silenceFrame(), FRAME, silenceThresholdSeconds = 0.1f)
        assertEquals(SpeechTransition.AutoStop, autoStop)
    }

    @Test
    fun `resumed speech during trailing silence resets the countdown`() {
        speak(0.5f, silenceThresholdSeconds = 0.1f)
        detector.onFrame(silenceFrame(), frameDurationSeconds = 0.08f, silenceThresholdSeconds = 0.1f)

        val resumed = detector.onFrame(toneFrame(0.5f), FRAME, silenceThresholdSeconds = 0.1f)
        assertEquals(SpeechTransition.ResumedSpeaking, resumed)
        assertEquals(SpeechState.SPEAKING, detector.state)

        // Silence budget should have reset, not immediately auto-stop on the next silent frame.
        val next = detector.onFrame(silenceFrame(), frameDurationSeconds = 0.08f, silenceThresholdSeconds = 0.1f)
        assertTrue(next is SpeechTransition.SilenceProgress)
    }

    @Test
    fun `ambient noise floor raises the effective stop threshold so silence at that level still triggers auto-stop`() {
        // Ambient noise sample: LISTENING frames at 0.05 (above the static stop threshold of 0.04,
        // but below the start threshold) establish a nonzero noise floor, simulating a room that
        // is never truly silent.
        repeat(20) {
            val transition = detector.onFrame(toneFrame(0.05f), FRAME, silenceThresholdSeconds = 0.1f)
            assertEquals(SpeechTransition.None, transition)
        }
        assertEquals(SpeechState.LISTENING, detector.state)

        speak(0.5f, silenceThresholdSeconds = 0.1f)
        assertEquals(SpeechState.SPEAKING, detector.state)

        // Returning to the same 0.05 ambient level that used to sit above the static stop
        // threshold should now register as silence, since the effective threshold was raised
        // above the measured noise floor.
        val afterSpeech = detector.onFrame(toneFrame(0.05f), FRAME, silenceThresholdSeconds = 0.1f)
        assertTrue(afterSpeech is SpeechTransition.SilenceProgress)
        assertEquals(SpeechState.TRAILING_SILENCE, detector.state)
    }

    @Test
    fun `speech that ramps in gradually cannot ratchet the noise floor out of its own reach`() {
        // The regression this guards: unrecognized speech used to feed the noise floor, raising
        // the start threshold ahead of the ramp, so the take sat in LISTENING through the word.
        val quiet = SpeechEndpointDetector(SpeechDetectorConfig())
        repeat(50) { quiet.onFrame(toneFrame(0.003f), FRAME, silenceThresholdSeconds = 1.5f) }
        assertEquals(SpeechState.LISTENING, quiet.state)

        // A soft onset climbing through the threshold over ~300ms.
        var started: SpeechTransition = SpeechTransition.None
        for (step in 1..15) {
            val transition = quiet.onFrame(toneFrame(0.002f * step), FRAME, silenceThresholdSeconds = 1.5f)
            if (transition != SpeechTransition.None) {
                started = transition
                break
            }
        }
        assertEquals(SpeechTransition.StartedSpeaking, started)
    }

    @Test
    fun `a transient does not leave the start threshold inflated against later speech`() {
        val quiet = SpeechEndpointDetector(SpeechDetectorConfig())
        repeat(10) { quiet.onFrame(toneFrame(0.002f), FRAME, silenceThresholdSeconds = 1.5f) }
        // A knock on the table: loud, but a single frame, so not speech.
        assertEquals(SpeechTransition.None, quiet.onFrame(toneFrame(0.6f), FRAME, silenceThresholdSeconds = 1.5f))
        repeat(10) { quiet.onFrame(toneFrame(0.002f), FRAME, silenceThresholdSeconds = 1.5f) }

        // Quiet speech right afterwards still has to register.
        var started: SpeechTransition = SpeechTransition.None
        repeat(3) {
            val transition = quiet.onFrame(toneFrame(0.03f), FRAME, silenceThresholdSeconds = 1.5f)
            if (transition != SpeechTransition.None) started = transition
        }
        assertEquals(SpeechTransition.StartedSpeaking, started)
    }

    @Test
    fun `near-threshold energy that never confirms is reported, silence is not`() {
        val quiet = SpeechEndpointDetector(SpeechDetectorConfig())
        repeat(50) { quiet.onFrame(silenceFrame(), FRAME, silenceThresholdSeconds = 1.5f) }
        assertEquals(0f, quiet.unconfirmedEnergySeconds, 1e-4f)

        // Audible, in the neighbourhood of the threshold, but never over it.
        repeat(50) { quiet.onFrame(toneFrame(0.008f), FRAME, silenceThresholdSeconds = 1.5f) }
        assertEquals(SpeechState.LISTENING, quiet.state)
        assertTrue(quiet.unconfirmedEnergySeconds >= 0.5f)
    }

    @Test
    fun `reset returns detector to listening state`() {
        speak(0.5f)
        assertNotEquals(SpeechState.LISTENING, detector.state)
        detector.reset()
        assertEquals(SpeechState.LISTENING, detector.state)
        assertEquals(0f, detector.unconfirmedEnergySeconds, 1e-4f)
        assertEquals(0f, detector.noiseFloorPeak, 1e-4f)
    }
}
