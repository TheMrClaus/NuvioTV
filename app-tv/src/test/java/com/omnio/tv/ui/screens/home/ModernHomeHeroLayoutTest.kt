package com.omnio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernHomeHeroLayoutTest {

    @Test
    fun `hero top right cluster keeps cast icon disabled and clock inset away from edge`() {
        val treatment = modernHeroTopRightClusterTreatment()

        assertFalse(treatment.showCastIcon)
        assertTrue(treatment.endPaddingDp >= 56)
        assertFalse(heroTopRightClusterShowsCastIcon(treatment))
    }

    @Test
    fun `hero top right cluster cast icon visibility follows treatment`() {
        assertTrue(
            heroTopRightClusterShowsCastIcon(
                ModernHeroTopRightClusterTreatment(
                    showCastIcon = true,
                    endPaddingDp = 56
                )
            )
        )
    }

    @Test
    fun `hero and rows share the same tighter left start`() {
        assertEquals(32, modernHomeHeroStartPaddingDp())
        assertEquals(
            modernHomeHeroStartPaddingDp(),
            modernHomeRowStartPaddingDp()
        )
    }
}
