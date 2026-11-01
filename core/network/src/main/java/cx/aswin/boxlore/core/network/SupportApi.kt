package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.model.SupportListResponse
import cx.aswin.boxlore.core.network.model.SupportNewThreadRequest
import cx.aswin.boxlore.core.network.model.SupportPhotoResponse
import cx.aswin.boxlore.core.network.model.SupportThreadResponse
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

private const val NO_STORE = "Cache-Control: no-store"

/**
 * Support conversations (`/support/...`). Requests carry the API session from the shared client
 * and a device-key proof, which is how the API knows which install a conversation belongs to.
 * Every label, status and limit comes from the responses, so they change without an app update.
 * Nothing is kept in the HTTP cache. Build it with [NetworkModule.createSupportApi].
 */
interface SupportApi {
    /** `meta` (switches, limits, categories, push topics) and this install's conversations. */
    @Headers(NO_STORE)
    @GET("support/threads")
    suspend fun threads(): Response<SupportListResponse>

    @Headers(NO_STORE)
    @POST("support/threads")
    suspend fun startThread(@Body request: SupportNewThreadRequest): Response<SupportThreadResponse>

    /** Raw JPEG, PNG or WebP bytes; answers 403 while photos are switched off. */
    @Headers(NO_STORE)
    @POST("support/photos")
    suspend fun uploadPhoto(@Body photo: RequestBody): Response<SupportPhotoResponse>
}
