package com.omnio.tv.data.remote.dto.aiostreams

import com.omnio.tv.domain.model.AioStreamsConfigInnerDto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.lang.reflect.Type

/**
 * AIOStreams persists a large, evolving userData object. Keep it opaque and
 * round-trip the root object verbatim rather than modeling each field.
 */
class AioStreamsConfigInnerDtoJsonAdapter(moshi: Moshi) : JsonAdapter<AioStreamsConfigInnerDto>() {

    private val mapAdapter: JsonAdapter<Map<String, Any?>> = moshi.adapter(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    override fun fromJson(reader: JsonReader): AioStreamsConfigInnerDto {
        val raw = mapAdapter.fromJson(reader) ?: emptyMap()
        return AioStreamsConfigInnerDto(config = raw)
    }

    override fun toJson(writer: JsonWriter, value: AioStreamsConfigInnerDto?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        mapAdapter.toJson(writer, value.config)
    }

    companion object Factory : JsonAdapter.Factory {
        override fun create(
            type: Type,
            annotations: Set<Annotation>,
            moshi: Moshi,
        ): JsonAdapter<*>? {
            if (annotations.isNotEmpty()) return null
            if (Types.getRawType(type) != AioStreamsConfigInnerDto::class.java) return null
            return AioStreamsConfigInnerDtoJsonAdapter(moshi)
        }
    }
}
