package com.omnio.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernSidebarBlurPanelTest {

    @Test
    fun `selected item uses red accent border and wash`() {
        val treatment = sidebarNavigationItemTreatment(
            selected = true,
            focused = false
        )

        assertTrue(treatment.showActiveIndicator)
        assertEquals(SidebarItemTone.Active, treatment.tone)
    }

    @Test
    fun `focused unselected item keeps focus tone without active border`() {
        val treatment = sidebarNavigationItemTreatment(
            selected = false,
            focused = true
        )

        assertFalse(treatment.showActiveIndicator)
        assertEquals(SidebarItemTone.Focused, treatment.tone)
    }

    @Test
    fun `idle item keeps neutral tone`() {
        val treatment = sidebarNavigationItemTreatment(
            selected = false,
            focused = false
        )

        assertFalse(treatment.showActiveIndicator)
        assertEquals(SidebarItemTone.Idle, treatment.tone)
    }
}
