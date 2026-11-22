/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.flaccore.monochrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlacStreamInfoTest {

    @Test
    fun `parse valid 16-bit 44_1kHz FLAC header`() {
        val bytes = createFlacHeader(bitsPerSample = 16, sampleRate = 44_100)
        val info = FlacStreamInfo.parse(bytes)
        assertNotNull(info)
        assertEquals(16, info!!.bitsPerSample)
        assertEquals(44_100, info.sampleRateHz)
        assertEquals("FLAC", info.shortLabel)
    }

    @Test
    fun `parse valid 24-bit 96kHz Hi-Res FLAC header`() {
        val bytes = createFlacHeader(bitsPerSample = 24, sampleRate = 96_000)
        val info = FlacStreamInfo.parse(bytes)
        assertNotNull(info)
        assertEquals(24, info!!.bitsPerSample)
        assertEquals(96_000, info.sampleRateHz)
        assertEquals("Hi-Res FLAC", info.shortLabel)
        assertTrue(info.isHiRes)
    }

    @Test
    fun `returns null on invalid header magic`() {
        val bytes = "RIFF1234WAVEfmt ".toByteArray()
        val info = FlacStreamInfo.parse(bytes)
        assertNull(info)
    }

    @Test
    fun `returns null on too short byte array`() {
        val bytes = byteArrayOf('f'.code.toByte(), 'L'.code.toByte())
        val info = FlacStreamInfo.parse(bytes)
        assertNull(info)
    }

    private fun createFlacHeader(bitsPerSample: Int, sampleRate: Int): ByteArray {
        val data = ByteArray(42)
        val magic = "fLaC".toByteArray()
        System.arraycopy(magic, 0, data, 0, 4)
        // bytes 4..7: metadata block header
        data[4] = 0 // last-block flag (0) + block type (0 = STREAMINFO)
        data[7] = 34 // block length = 34

        // inside STREAMINFO (offset 8 in data):
        // streamInfo[10] = data[18]
        // streamInfo[11] = data[19]
        // streamInfo[12] = data[20]
        // streamInfo[13] = data[21]
        data[18] = ((sampleRate shr 12) and 0xff).toByte()
        data[19] = ((sampleRate shr 4) and 0xff).toByte()
        val bpsIdx = bitsPerSample - 1
        val channelsIdx = 1 // 2 channels - 1
        data[20] = (((sampleRate and 0x0f) shl 4) or (channelsIdx shl 1) or ((bpsIdx shr 4) and 0x01)).toByte()
        data[21] = (((bpsIdx and 0x0f) shl 4)).toByte()
        return data
    }
}
