package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.model.SupportListResponse
import cx.aswin.boxlore.core.network.model.SupportNewThreadRequest
import cx.aswin.boxlore.core.network.model.SupportPhotoResponse
import cx.aswin.boxlore.core.network.model.SupportThreadResponse
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Support conversations (`/support/...`). Requests carry the API session from the shared client,
 * which is how the API knows which install a conversation belongs to. Every label, status and
 * limit comes from the responses, so they change without an app update.
 * Build it with [NetworkModule.createSupportApi] on the same cached client as [BoxLoreApi].
 */
interface SupportApi {
    /** `meta` (switches, limits, push topics) and this install's conversations. */
    @GET("support/threads")
    fun threads(): Call<SupportListResponse>

    @POST("support/threads")
    fun startThread(@Body request: SupportNewThreadRequest): Call<SupportThreadResponse>

    /** Raw JPEG, PNG or WebP bytes; answers 403 while photos are switched off. */
    @POST("support/photos")
    fun uploadPhoto(@Body photo: RequestBody): Call<SupportPhotoResponse>
}
