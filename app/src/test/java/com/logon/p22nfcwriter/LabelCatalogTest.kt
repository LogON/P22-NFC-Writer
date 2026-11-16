package com.logon.p22nfcwriter

import com.logon.p22nfcwriter.model.LabelCatalog
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelCatalogTest {
    // Excerpt of real /api/label/getLabelList responses for P22 and P21 (extra fields removed).
    private val rows = JSONArray(
        """
        [
         {"code":"011205000301121215280F0E","name":"14x40mm GAP05","type":1,"labelWidth":40.0,"labelHeight":14.0,"hasBg":1,"modelType":"P21,P22","image":"https://oss.nelko.net/a.png"},
         {"code":"0122030003000012150A0F0C","name":"12mm CONTINUOUS01","type":0,"labelWidth":10.0,"labelHeight":12.0,"hasBg":0,"modelType":"P22","image":"https://oss.nelko.net/b.png"},
         {"code":"011205000301121215280F0E","name":"14x40mm GAP05","type":1,"labelWidth":40.0,"labelHeight":14.0,"hasBg":1,"modelType":"P21,P22","image":"https://oss.nelko.net/a.png"},
         {"code":"0122470003010512154B0F0E","name":"14x75+47mm","type":1,"labelWidth":75.0,"labelHeight":14.0,"hasBg":1,"modelType":"P31S,P21","image":null},
         {"code":"","name":"broken","type":1,"labelWidth":40.0,"labelHeight":14.0,"hasBg":0}
        ]
        """
    )

    @Test
    fun parsesAndMergesServerRows() {
        val entries = LabelCatalog.parseServerRows(rows)
        // No duplicates; order as in the bundled list: by category string, then by code.
        assertEquals(listOf("14x40mm GAP05", "14x75+47mm", "12mm CONTINUOUS01"), entries.map { it.name })

        val gap = entries[0]
        assertEquals("14x40mm", gap.category)
        assertEquals(40, gap.length)
        assertTrue(gap.decor)
        assertTrue(gap.forP22)
        assertEquals("https://oss.nelko.net/a.png", gap.imageUrl)

        val cable = entries[1]
        assertFalse(cable.forP22)
        assertEquals("14x75+47mm [P31S,P21]", cable.toString())
        assertEquals(null, cable.imageUrl)

        val cont = entries[2]
        assertEquals("Continuous 12mm", cont.category)
        assertEquals(12, cont.width)
    }
}
