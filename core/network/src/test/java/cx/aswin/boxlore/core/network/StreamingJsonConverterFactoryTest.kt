package cx.aswin.boxlore.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import retrofit2.Retrofit

class StreamingJsonConverterFactoryTest {

    @Serializable
    private data class TestDto(val id: String, val count: Int)

    private val contentType = "application/json; charset=utf-8".toMediaType()
    private val json = Json { ignoreUnknownKeys = true }
    private val factory = StreamingJsonConverterFactory.create(contentType, json)
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .addConverterFactory(factory)
        .build()

    @Test
    fun `responseBodyConverter streams and deserializes JSON model`() {
        val converter = factory.responseBodyConverter(
            TestDto::class.java,
            emptyArray(),
            retrofit,
        )
        assertNotNull(converter)

        val jsonString = """{"id":"show_42","count":123}"""
        val responseBody = jsonString.toResponseBody(contentType)

        @Suppress("UNCHECKED_CAST")
        val result = (converter as retrofit2.Converter<okhttp3.ResponseBody, Any>).convert(responseBody) as TestDto
        assertEquals("show_42", result.id)
        assertEquals(123, result.count)
    }

    @Test
    fun `responseBodyConverter throws SerializationException on empty response body`() {
        val converter = factory.responseBodyConverter(
            TestDto::class.java,
            emptyArray(),
            retrofit,
        )
        assertNotNull(converter)

        val responseBody = "".toResponseBody(contentType)

        @Suppress("UNCHECKED_CAST")
        val typedConverter = converter as retrofit2.Converter<okhttp3.ResponseBody, Any>
        org.junit.jupiter.api.assertThrows<kotlinx.serialization.SerializationException> {
            typedConverter.convert(responseBody)
        }
    }

    @Test
    fun `responseBodyConverter throws SerializationException on malformed JSON`() {
        val converter = factory.responseBodyConverter(
            TestDto::class.java,
            emptyArray(),
            retrofit,
        )
        assertNotNull(converter)

        val responseBody = "{bad json}".toResponseBody(contentType)

        @Suppress("UNCHECKED_CAST")
        val typedConverter = converter as retrofit2.Converter<okhttp3.ResponseBody, Any>
        org.junit.jupiter.api.assertThrows<kotlinx.serialization.SerializationException> {
            typedConverter.convert(responseBody)
        }
    }

    @Test
    fun `requestBodyConverter serializes model into request body`() {
        val converter = factory.requestBodyConverter(
            TestDto::class.java,
            emptyArray(),
            emptyArray(),
            retrofit,
        )
        assertNotNull(converter)

        @Suppress("UNCHECKED_CAST")
        val requestBody = (converter as retrofit2.Converter<Any, okhttp3.RequestBody>).convert(TestDto("p_99", 5))
        assertNotNull(requestBody)
        assertEquals(contentType, requestBody?.contentType())

        val buffer = okio.Buffer()
        requestBody?.writeTo(buffer)
        assertEquals("""{"id":"p_99","count":5}""", buffer.readUtf8())
    }
}
