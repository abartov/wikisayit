package wiki.asaf.wikisayit.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingPaddingTest {
    @Test
    fun `adds silence to both ends sized by sample rate`() {
        val input = shortArrayOf(10, 20, 30)
        val result = addPadding(input, sampleRate = 100, paddingSeconds = 0.2f)

        // 0.2s at 100Hz = 20 samples of silence on each side.
        assertEquals(20 + 3 + 20, result.size)
        assertArrayEquals(ShortArray(20), result.copyOfRange(0, 20))
        assertArrayEquals(input, result.copyOfRange(20, 23))
        assertArrayEquals(ShortArray(20), result.copyOfRange(23, 43))
    }

    @Test
    fun `zero padding returns input unchanged`() {
        val input = shortArrayOf(1, 2, 3)
        assertArrayEquals(input, addPadding(input, sampleRate = 44_100, paddingSeconds = 0f))
    }
}
