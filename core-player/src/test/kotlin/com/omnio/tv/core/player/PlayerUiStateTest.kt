package com.omnio.tv.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlayerUiStateTest {
    @Test
    fun `hide stream source indicator clears persistent source banner`() {
        val state = PlayerUiState(
            showStreamSourceIndicator = true,
            streamSourceIndicatorText = "Source: Emby"
        )

        val hidden = state.hideStreamSourceIndicator()

        assertFalse(hidden.showStreamSourceIndicator)
        assertEquals("", hidden.streamSourceIndicatorText)
    }
}
