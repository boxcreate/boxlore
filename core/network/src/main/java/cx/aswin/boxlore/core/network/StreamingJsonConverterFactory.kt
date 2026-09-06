package cx.aswin.boxlore.core.network

import java.lang.reflect.Type
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit

/**
 * A Retrofit [Converter.Factory] that deserializes JSON directly from OkHttp's response [java.io.InputStream]
 * using [Json.decodeFromStream], avoiding monolithic [ResponseBody.string] heap buffer allocations.
 */
@OptIn(ExperimentalSerializationApi::class)
class StreamingJsonConverterFactory private constructor(
    private val contentType: MediaType,
    private val json: Json,
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<ResponseBody, *> {
        val serializer = json.serializersModule.serializer(type)
        return Converter<ResponseBody, Any?> { body ->
            body.use { responseBody ->
                json.decodeFromStream(serializer, responseBody.byteStream())
            }
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): Converter<*, RequestBody> {
        val serializer = json.serializersModule.serializer(type)
        return Converter<Any, RequestBody> { value ->
            val string = json.encodeToString(serializer, value)
            string.toRequestBody(contentType)
        }
    }

    companion object {
        fun create(contentType: MediaType, json: Json): StreamingJsonConverterFactory =
            StreamingJsonConverterFactory(contentType, json)
    }
}
