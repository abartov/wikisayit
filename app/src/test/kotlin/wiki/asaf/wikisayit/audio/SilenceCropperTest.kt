package wiki.asaf.wikisayit.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

private fun samples(vararg amplitudes: Float): ShortArray =
    amplitudes.map {
        (it * 32768f).toInt().toShort()
    }.toShortArray()

class SilenceCropperTest {
    @Test
    fun `trims leading and trailing silence`() {
        val input = samples(0f, 0f, 0.5f, 0.6f, 0.5f, 0f, 0f)
        val result = cropSilence(input, threshold = 0.04f)
        assertArrayEquals(samples(0.5f, 0.6f, 0.5f), result)
    }

    @Test
    fun `leaves loud recording untouched`() {
        val input = samples(0.5f, 0.6f, 0.5f)
        val result = cropSilence(input, threshold = 0.04f)
        assertArrayEquals(input, result)
    }

    @Test
    fun `returns empty array when entirely below threshold`() {
        val input = samples(0f, 0.01f, 0f)
        val result = cropSilence(input, threshold = 0.04f)
        assertEquals(0, result.size)
    }

    @Test
    fun `keeps a margin of original audio around the speech`() {
        val input = samples(0f, 0.01f, 0.02f, 0.5f, 0.6f, 0.02f, 0.01f, 0f)
        val result = cropSilence(input, threshold = 0.04f, marginSamples = 2)
        assertArrayEquals(samples(0.01f, 0.02f, 0.5f, 0.6f, 0.02f, 0.01f), result)
    }

    @Test
    fun `margin is clamped to the ends of the recording`() {
        val input = samples(0.01f, 0.5f, 0.6f, 0.02f)
        val result = cropSilence(input, threshold = 0.04f, marginSamples = 5)
        assertArrayEquals(input, result)
    }

    @Test
    fun `handles empty input`() {
        assertEquals(0, cropSilence(ShortArray(0)).size)
    }
}
