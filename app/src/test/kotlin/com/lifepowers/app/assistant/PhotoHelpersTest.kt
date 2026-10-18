package com.lifepowers.app.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoHelpersTest {

    @Test
    fun sampleSizeKeepsLongSideAtOrAboveTarget() {
        assertEquals(1, PhotoSizing.sampleSize(1200, 900))
        assertEquals(2, PhotoSizing.sampleSize(4032, 3024))
        assertEquals(4, PhotoSizing.sampleSize(8000, 6000))
    }

    @Test
    fun fitScalesTheLongSide() {
        assertEquals(1568 to 1176, PhotoSizing.fit(4032, 3024))
        assertEquals(1176 to 1568, PhotoSizing.fit(3024, 4032))
        assertEquals(800 to 600, PhotoSizing.fit(800, 600))
    }

    private fun jpeg(vararg segments: ByteArray): ByteArray {
        val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        val sos = byteArrayOf(0xFF.toByte(), 0xDA.toByte(), 0x00, 0x02, 0x11, 0x22)
        return segments.fold(soi) { acc, s -> acc + s } + sos
    }

    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val len = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (len shr 8).toByte(), (len and 0xFF).toByte()) + payload
    }

    @Test
    fun jfifOnlyIsClean() {
        assertTrue(PhotoSizing.isCleanJpeg(jpeg(segment(0xE0, "JFIF\u0000".toByteArray()), segment(0xDB, ByteArray(65)))))
    }

    @Test
    fun exifOrCommentsAreNotClean() {
        assertFalse(PhotoSizing.isCleanJpeg(jpeg(segment(0xE1, "Exif\u0000\u0000GPS".toByteArray()))))
        assertFalse(PhotoSizing.isCleanJpeg(jpeg(segment(0xFE, "made on a phone".toByteArray()))))
        assertFalse(PhotoSizing.isCleanJpeg(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
    }

    @Test
    fun qaBlockSkipsUnanswered() {
        val block = PhotoNotes.qaBlock(listOf("Is this the garden bed?", "How long?", "Who helped?"), listOf("Yes, the new one", "", "My sister\nand me"))
        assertEquals("Q: Is this the garden bed?\nA: Yes, the new one\nQ: Who helped?\nA: My sister and me", block)
        assertEquals("", PhotoNotes.qaBlock(listOf("A?"), listOf(" ")))
    }

    @Test
    fun appendBlockAddsABlankLine() {
        assertEquals("Q: A?\nA: b", PhotoNotes.appendBlock("", "Q: A?\nA: b"))
        assertEquals("Morning walk.\n\nQ: A?\nA: b", PhotoNotes.appendBlock("Morning walk.\n", "Q: A?\nA: b"))
        assertEquals("Same", PhotoNotes.appendBlock("Same", " "))
    }

    @Test
    fun linesStayAligned() {
        val joined = PhotoNotes.joinLines(listOf("one", "two\nlines", ""))
        assertEquals("one\ntwo lines\n", joined)
        assertEquals(listOf("one", "two lines", ""), PhotoNotes.splitLines(joined))
        assertEquals(emptyList<String>(), PhotoNotes.splitLines(""))
    }
}
