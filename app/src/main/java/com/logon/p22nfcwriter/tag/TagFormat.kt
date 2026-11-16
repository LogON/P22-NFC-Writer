package com.logon.p22nfcwriter.tag

/**
 * P21/P22 roll tag format (NTAG203/213), reconstructed from Labelnise
 * (com.ezink.data.ConsumablesPaperBean.parse) and the server label catalog.
 *
 * Page 4: 01 03 A0 0C. The last byte (0x0C = 12) is the record length; the app checks it.
 * Pages 5..7: the 12-byte "paper code". The app uses it to look up the template in the catalog
 * (size, gap, background design). There is no encryption or checksum on the tag.
 * Details: docs/TAG_FORMAT.md.
 */
object TagFormat {
    const val FIRST_PAGE = 4
    private const val RECORD_LENGTH: Byte = 0x0C
    val PAGE4 = byteArrayOf(0x01, 0x03, 0xA0.toByte(), RECORD_LENGTH)

    fun parseCode(hex: String): ByteArray? {
        val clean = hex.filterNot { it.isWhitespace() || it == ':' || it == '-' }
        if (clean.length != 24 || !clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return ByteArray(12) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    /** Pages 4..7 to write. */
    fun pages(code: ByteArray): List<Pair<Int, ByteArray>> {
        require(code.size == 12) { "code must be 12 bytes" }
        return listOf(
            4 to PAGE4,
            5 to code.copyOfRange(0, 4),
            6 to code.copyOfRange(4, 8),
            7 to code.copyOfRange(8, 12),
        )
    }

    /** 16 bytes read by READ(4) = pages 4..7 → the code, or null if this is not a roll tag. */
    fun codeFromRead(data: ByteArray): ByteArray? {
        if (data.size < 16 || data[3] != RECORD_LENGTH) return null
        return data.copyOfRange(4, 16)
    }

    data class Decoded(
        val format: Int,     // k1: 0x12 — 14 mm, 0x02 — 12/15 mm, 0x22 — tape/cable
        val design: Int,     // k2: colour/design number in the catalog
        val paperType: Int,  // k5: 0 — continuous tape, 1 — with gap
        val lengthMm: Int,   // k9
        val widthMm: Int,    // k10 for format 0x02, otherwise k11
    )

    fun decode(code: ByteArray): Decoded {
        require(code.size == 12)
        val u = { i: Int -> code[i].toInt() and 0xFF }
        val format = u(1)
        val width = if (format == 0x02) u(10) else u(11)
        val type = if (format shr 4 >= 2) u(5) and 0x0F else u(5)
        return Decoded(format, u(2), type, u(9), width)
    }
}
