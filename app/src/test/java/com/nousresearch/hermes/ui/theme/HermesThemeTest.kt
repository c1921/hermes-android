package com.nousresearch.hermes.ui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class HermesThemeTest {
    @Test
    fun `built in skins match the desktop contract`() {
        assertEquals(
            listOf("nous", "midnight", "ember", "mono", "cyberpunk", "slate"),
            HermesSkin.entries.map(HermesSkin::id),
        )

        assertEquals(
            mapOf(
                HermesSkin.NOUS to "FF0D2F86,FFFFE6CB,FF12378F,FFFFE6CB,FF183F9A,FFB5C7F3,FF123A96,FFFFE6CB,FFFFE6CB,FF0D2F86,FF1B45A4,FFE0E8FF,FF1540B1,FFF0F4FF,FF3158AD,FF0B2566,FFFFE6CB,FF0053FD,FFC0473A,FFFEF2F2,FF09286F,FF234A9C,FF143B91,FF3A63BD",
                HermesSkin.MIDNIGHT to "FF08081C,FFDDD6FF,FF0D0D28,FFDDD6FF,FF13133A,FF7C7AB0,FF0F0F2E,FFDDD6FF,FFDDD6FF,FF08081C,FF1A1A4A,FFC4BFF0,FF1A1A44,FFD0C8FF,FF1E1E52,FF1E1E52,FF8B80E8,FF8B80E8,FFB03060,FFFEF2F2,FF06061A,FF12123A,FF14143A,FF242466",
                HermesSkin.EMBER to "FF160800,FFFFD8B0,FF1E0E04,FFFFD8B0,FF2A1408,FFAA7A56,FF221008,FFFFD8B0,FFFFD8B0,FF160800,FF341800,FFF0C090,FF301600,FFE8C080,FF3A1C08,FF3A1C08,FFD97316,FFD97316,FFC43010,FFFEF2F2,FF100600,FF2A1004,FF2A1000,FF4A2010",
                HermesSkin.MONO to "FF0E0E0E,FFEAEAEA,FF141414,FFEAEAEA,FF1E1E1E,FF808080,FF181818,FFEAEAEA,FFEAEAEA,FF0E0E0E,FF262626,FFC8C8C8,FF222222,FFD8D8D8,FF2A2A2A,FF2A2A2A,FF9A9A9A,FF9A9A9A,FFA84040,FFFEF2F2,FF0A0A0A,FF202020,FF1A1A1A,FF363636",
                HermesSkin.CYBERPUNK to "FF000A00,FF00FF41,FF001200,FF00FF41,FF001A00,FF1A8A30,FF001000,FF00FF41,FF00FF41,FF000A00,FF002800,FF00CC34,FF002000,FF00E038,FF003000,FF003000,FF00FF41,FF00FF41,FFFF003C,FF000A00,FF000600,FF001800,FF001400,FF004800",
                HermesSkin.SLATE to "FF0D1117,FFC9D1D9,FF161B22,FFC9D1D9,FF21262D,FF8B949E,FF1C2128,FFC9D1D9,FFC9D1D9,FF0D1117,FF2A3038,FFADB5BF,FF1E2530,FFC0C8D0,FF30363D,FF30363D,FF58A6FF,FF58A6FF,FFCF4848,FFFEF2F2,FF090D13,FF1C2228,FF1E2A38,FF2E4060",
            ),
            HermesSkin.entries.associateWith { it.palette(dark = true).fingerprint() },
        )
    }

    @Test
    fun `light palettes use the desktop source mixes`() {
        val nous = HermesSkin.NOUS.palette(dark = false)
        assertEquals(0xFFF2F6FF.toInt(), nous.muted.toArgb())
        assertEquals(0xFFEDF3FF.toInt(), nous.secondary.toArgb())
        assertEquals(0xFFE6EEFF.toInt(), nous.accent.toArgb())
        assertEquals(0x380053FD, nous.border.toArgb())

        val midnight = HermesSkin.MIDNIGHT.palette(dark = false)
        assertEquals(0xFFF3F2FD.toInt(), midnight.secondary.toArgb())
        assertEquals(0xFFF8F7FE.toInt(), midnight.muted.toArgb())
        assertEquals(0xFFDEDDEE.toInt(), midnight.border.toArgb())
        assertEquals(0xFF706E83.toInt(), midnight.mutedForeground.toArgb())
    }

    @Test
    fun `unknown persisted skin returns to nous`() {
        assertEquals(HermesSkin.NOUS, HermesSkin.fromId("retired-theme"))
        assertEquals(HermesSkin.NOUS, HermesSkin.fromId(null))
    }

    private fun HermesPalette.fingerprint(): String = listOf(
        background, foreground, card, cardForeground, muted, mutedForeground,
        popover, popoverForeground, primary, primaryForeground, secondary,
        secondaryForeground, accent, accentForeground, border, input, ring,
        midground, destructive, destructiveForeground, sidebarBackground,
        sidebarBorder, userBubble, userBubbleBorder,
    ).joinToString(",") { "%08X".format(it.toArgb()) }
}
