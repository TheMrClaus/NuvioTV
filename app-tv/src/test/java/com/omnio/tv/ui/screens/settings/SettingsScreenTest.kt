package com.omnio.tv.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsScreenTest {

    @Test
    fun `header meta segments omit unavailable bandwidth`() {
        assertEquals(
            listOf("Profile: Claus", "v0.4.2-tv (build 1284)"),
            settingsHeaderMetaSegments(
                profileName = "Claus",
                versionName = "0.4.2-tv",
                buildNumber = "1284",
                bandwidthLabel = null
            )
        )
    }

    @Test
    fun `header meta segments include bandwidth when available`() {
        assertEquals(
            listOf("Profile: Claus", "v0.4.2-tv (build 1284)", "940 Mbps"),
            settingsHeaderMetaSegments(
                profileName = "Claus",
                versionName = "0.4.2-tv",
                buildNumber = "1284",
                bandwidthLabel = "940 Mbps"
            )
        )
    }

    @Test
    fun `section count chip is only shown for supported categories`() {
        assertEquals("4", settingsSectionCountLabel(SettingsCategory.PROFILES, profileCount = 4))
        assertNull(settingsSectionCountLabel(SettingsCategory.PLUGINS, profileCount = 4))
        assertNull(settingsSectionCountLabel(SettingsCategory.ABOUT, profileCount = 4))
    }
}
