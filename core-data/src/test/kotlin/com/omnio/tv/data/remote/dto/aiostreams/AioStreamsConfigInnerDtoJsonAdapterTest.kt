package com.omnio.tv.data.remote.dto.aiostreams

import com.omnio.tv.domain.model.AioStreamsConfigInnerDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class AioStreamsConfigInnerDtoJsonAdapterTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(AioStreamsConfigInnerDtoJsonAdapter.Factory)
        .add(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(AioStreamsConfigInnerDto::class.java)

    @Test
    fun `toJson writes config object verbatim`() {
        val dto = AioStreamsConfigInnerDto(
            config = mapOf(
                "formatter" to mapOf("id" to "gdrive"),
                "services" to listOf(mapOf("id" to "realdebrid", "enabled" to false)),
                "checkOwned" to true,
            )
        )

        @Suppress("UNCHECKED_CAST")
        val decoded = moshi.adapter(Map::class.java).fromJson(adapter.toJson(dto)) as Map<String, Any?>

        assertEquals(true, decoded["checkOwned"])
        assertEquals(mapOf("id" to "gdrive"), decoded["formatter"])
    }

    @Test
    fun `fromJson reads root object into config`() {
        val json = """
            {
              "formatter": { "id": "gdrive" },
              "checkOwned": true,
              "services": [ { "id": "realdebrid", "enabled": false, "credentials": {} } ]
            }
        """.trimIndent()

        val decoded = adapter.fromJson(json)!!

        assertEquals(true, decoded.config["checkOwned"])
        assertEquals(mapOf("id" to "gdrive"), decoded.config["formatter"])
    }
}
