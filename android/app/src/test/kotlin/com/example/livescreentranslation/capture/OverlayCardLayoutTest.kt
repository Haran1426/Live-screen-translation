package com.example.livescreentranslation.capture

import org.junit.Assert.*
import org.junit.Test

class OverlayCardLayoutTest {
    @Test fun portraitKeepsExistingNormalSize() {
        assertEquals(OverlayCardLayout(387, 300, false), OverlayCardLayout.calculate(411, 914, 1f))
    }

    @Test fun landscapeKeepsReadingHeightAndLeavesSourceArea() {
        val layout = OverlayCardLayout.calculate(914, 411, 1f)
        assertEquals(300, layout.height)
        assertTrue(layout.width <= 914 / 2)
        assertTrue(layout.compact)
    }

    @Test fun shortLandscapeUsesHeightInsteadOfHalvingIt() {
        val layout = OverlayCardLayout.calculate(640, 320, 1f)
        assertEquals(272, layout.height)
        assertEquals(320, layout.width)
    }

    @Test fun largerTextGetsMoreHeightWithinMargins() {
        val layout = OverlayCardLayout.calculate(914, 411, 1.4f)
        assertEquals(363, layout.height)
        assertTrue(layout.height > OverlayCardLayout.calculate(914, 411, 1f).height)
    }

    @Test fun portraitRemainsAtMostHalfTheDisplay() {
        val layout = OverlayCardLayout.calculate(320, 600, 2f)
        assertEquals(300, layout.height)
        assertFalse(layout.compact)
    }

    @Test fun tallerPortraitCardLeavesAtLeastAThirdForSource() {
        val normal = OverlayCardLayout.calculate(411, 914, 1f)
        val tall = OverlayCardLayout.calculate(411, 914, 1f, 16, 420)
        assertTrue(tall.height > normal.height)
        assertTrue(tall.height <= 914 * 2 / 3)
        assertEquals(normal.width, tall.width)
    }

    @Test fun biggerBodyTextReceivesMoreReadingHeight() {
        val normal = OverlayCardLayout.calculate(411, 914, 1f)
        val larger = OverlayCardLayout.calculate(411, 914, 1f, 24)
        assertTrue(larger.height > normal.height)
        assertTrue(larger.height <= 914 / 2)
    }

    @Test fun allUserSizesAndSystemScalesStayInsideSmallScreens() {
        for ((width, height) in listOf(320 to 600, 640 to 320, 914 to 411)) {
            for (text in listOf(16, 20, 24)) for (card in listOf(300, 420)) for (scale in listOf(1f, 1.4f, 2f)) {
                val layout = OverlayCardLayout.calculate(width, height, scale, text, card)
                assertTrue(layout.height in 1..(height - 48))
                assertTrue(layout.width in 1..(width - 24))
                if (width > height) assertTrue(layout.width <= width / 2)
                else assertTrue(layout.height <= if (card == 420) height * 2 / 3 else height / 2)
            }
        }
    }
}
