package com.omnio.tv.data.local

import com.omnio.tv.domain.model.SourceCloudService
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceCloudSettingsDataStoreTest {
    @Test
    fun `service keys round trip through parser`() {
        val parsed = SourceCloudSettingsDataStore.parseServiceKeys("real_debrid,torbox,missing")

        assertEquals(setOf(SourceCloudService.REAL_DEBRID, SourceCloudService.TORBOX), parsed)
        assertEquals("real_debrid,torbox", SourceCloudSettingsDataStore.encodeServiceKeys(parsed))
    }
}
