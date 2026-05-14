package com.omnio.tv.ui.screens.home

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernHomeHeroLayoutTest {

    @Test
    fun `hero top right cluster keeps cast icon disabled and clock inset slightly inside edge`() {
        val treatment = modernHeroTopRightClusterTreatment()

        assertFalse(treatment.showCastIcon)
        assertEquals(44, treatment.endPaddingDp)
        assertFalse(heroTopRightClusterShowsCastIcon(treatment))
    }

    @Test
    fun `hero buttons use Omnio action palette`() {
        assertEquals(
            ModernHeroActionButtonTreatment(
                primaryContainer = HeroActionContainer.Accent,
                primaryContentColor = Color.White,
                secondaryContainer = HeroActionContainer.Card,
                secondaryContentColor = Color.White
            ),
            modernHeroActionButtonTreatment()
        )
    }

    @Test
    fun `hero actions are hidden only while rows are actively scrolling`() {
        assertFalse(shouldShowHeroActionRow(isRowsScrolling = true))
        assertTrue(shouldShowHeroActionRow(isRowsScrolling = false))
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
