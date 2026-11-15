package com.example.movieboxexplorer.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The mobile API reports unknown numbers as `""`; the models must not reject the
 * whole record because of it.
 */
class MobileModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Serializable
    private data class Probe(
        @Serializable(with = LenientIntSerializer::class) val duration: Int = 0,
    )

    @Test
    fun `a blank numeric string decodes to zero`() {
        assertEquals(0, json.decodeFromString<Probe>( """{"duration":""}""" ).duration)
    }

    @Test
    fun `a numeric string decodes to its value`() {
        assertEquals(1080, json.decodeFromString<Probe>( """{"duration":"1080"}""" ).duration)
    }

    @Test
    fun `a subject returned directly under data decodes`() {
        val element = json.parseToJsonElement(
            """{"subjectId":"6983322689440465144","subjectType":2,"title":"Black Torch","duration":""}""",
        )
        val subject = json.decodeFromJsonElement(Subject.serializer(), element)
        assertEquals("Black Torch", subject.title)
        assertEquals(6983322689440465144L, subject.subjectId.toLong())
        assertEquals(0, subject.duration)
    }

    @Test
    fun `a dub with a language field folds into lanCode`() {
        val dub = MobileDub(
            subjectId = "1",
            lanName = "English dub",
            language = "en",
        )
        assertEquals("en", dub.toDubVariant().lanCode)
    }
}