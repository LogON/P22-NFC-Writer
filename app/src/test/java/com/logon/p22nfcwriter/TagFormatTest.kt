package com.logon.p22nfcwriter

import com.logon.p22nfcwriter.tag.TagFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagFormatTest {
    private fun hex(s: String) = TagFormat.parseCode(s)!!

    @Test
    fun pagesMatchRealBlueRoll() {
        // docs/dumps/012.nfc, pages 4..7
        val pages = TagFormat.pages(hex("011205000301121215280F0E"))
        val expected = listOf("0103A00C", "01120500", "03011212", "15280F0E")
        assertEquals(listOf(4, 5, 6, 7), pages.map { it.first })
        assertEquals(expected, pages.map { TagFormat.toHex(it.second) })
    }

    @Test
    fun decodeWidthPerFormat() {
        assertEquals(14, TagFormat.decode(hex("011203000301121215280F0E")).widthMm)
        assertEquals(12, TagFormat.decode(hex("010204000301121215280C0F")).widthMm) // format 0x02: width in k10
        assertEquals(15, TagFormat.decode(hex("010203000301121215280F0F")).widthMm)
        val cont = TagFormat.decode(hex("0122030003000012150A0F0E")) // Olympia 6312 continuous tape
        assertEquals(0, cont.paperType)
        assertEquals(10, cont.lengthMm)
        assertEquals(14, cont.widthMm)
    }

    @Test
    fun parseCodeAcceptsSeparatorsAndRejectsBadInput() {
        assertArrayEquals(hex("011205000301121215280F0E"), TagFormat.parseCode("01 12 05 00:03-01 12 12 15 28 0f 0e"))
        assertNull(TagFormat.parseCode("0112"))
        assertNull(TagFormat.parseCode("ZZ1205000301121215280F0E"))
    }

    @Test
    fun codeFromReadNeedsRecordLength() {
        val read = TagFormat.PAGE4 + hex("011205000301121215280F0E")
        assertEquals("011205000301121215280F0E", TagFormat.toHex(TagFormat.codeFromRead(read)!!))
        assertNull(TagFormat.codeFromRead(ByteArray(16)))
    }
}
