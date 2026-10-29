package cx.aswin.boxlore.core.network

import android.util.Log
import cx.aswin.boxlore.core.network.session.ApiSessionInterceptor
import cx.aswin.boxlore.core.network.session.ApiSessionManager
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }
    private val contentType = "application/json".toMediaType()

    /**
     * App version (BuildConfig.VERSION_NAME), set by the app module at startup.
     * Sent as X-App-Version so the API can slice session adoption by build.
     */
    @Volatile
    var appVersion: String? = null

    /**
     * Install channel (`play` or `github`), set by the app module at startup.
     * Sent as X-App-Channel and in the session exchange.
     */
    @Volatile
    var appChannel: String? = null

    /**
     * This install's API session, set by the app module at startup. Requests carry
     * X-Boxlore-Session from it and never the bundled app key (see [ApiSessionInterceptor]).
     */
    @Volatile
    var sessionManager: ApiSessionManager? = null

    private val clientHeadersInterceptor = okhttp3.Interceptor { chain ->
        val builder = chain.request().newBuilder()
        appVersion?.let { builder.header("X-App-Version", it) }
        appChannel?.let { builder.header("X-App-Channel", it) }
        chain.proceed(builder.build())
    }

    private val sessionInterceptor = ApiSessionInterceptor { sessionManager }

    /**
     * Optional Authenticator (e.g. FirebaseAuthAuthenticator) for handling HTTP 401
     * token refreshes. Set by application wiring at startup.
     */
    @Volatile
    var authenticator: okhttp3.Authenticator? = null

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("BoxCastAPI", message)
    }.apply {
        level = if (cx.aswin.boxlore.core.network.BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        redactHeader("X-Firebase-AppCheck")
        redactHeader("X-App-Key")
        redactHeader("Authorization")
        redactHeader(ApiSessionInterceptor.SESSION_HEADER)
        redactHeader(ApiSessionInterceptor.RENEW_HEADER)
        redactHeader(ApiSessionInterceptor.PROOF_HEADER)
    }

    private val okHttpClient = OkHttpClient.Builder().apply {
        addInterceptor(EnglishOffInterceptor())
        addInterceptor(clientHeadersInterceptor)
        addInterceptor(sessionInterceptor)
        addInterceptor(loggingInterceptor)
        authenticator(
            okhttp3.Authenticator { route, response ->
            authenticator?.authenticate(route, response)
        }
        )
        connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
    }.build()

    /**
     * Plain client for `POST /auth/session`: no session interceptor (it would wait on itself), no
     * Firebase authenticator and no cache. Shares the connection pool with API calls.
     */
    fun createSessionExchangeClient(): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(okHttpClient.connectionPool)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * The shared API client with a 50 MiB HTTP cache in [context]'s cache directory. Create it once
     * per process and build every API version on it ([createBoxLoreApi], [createBoxLoreApiV2]): two
     * caches must never share a directory. There is deliberately no `createBoxLoreApi(baseUrl, context)`
     * shortcut, because each call would open another cache on the same directory.
     */
    fun createCachedClient(context: android.content.Context): OkHttpClient {
        val cacheSize = 50L * 1024L * 1024L // 50 MiB
        val cache = okhttp3.Cache(context.cacheDir, cacheSize)

        // Create a new client sharing the same connection pool/interceptors but with cache
        return okHttpClient.newBuilder()
            .cache(cache)
            .build()
    }

    /**
     * BoxCast API via Cloudflare Worker proxy, built on [client]. The base URL comes from BuildConfig
     * at runtime. Production passes the one client from [createCachedClient]; JVM tests pass a
     * MockWebServer or cache-less client.
     */
    fun createBoxLoreApi(baseUrl: String, client: OkHttpClient): BoxLoreApi = retrofit(baseUrl, client).create(BoxLoreApi::class.java)

    /**
     * Builds [BoxLoreApiV2] against [client]. Production passes the same cached client as
     * [BoxLoreApi] ([createCachedClient]); JVM tests pass a MockWebServer client.
     * Added in P4 of the own-episode-IDs plan (plan section 9).
     */
    fun createBoxLoreApiV2(baseUrl: String, client: OkHttpClient): BoxLoreApiV2 = retrofit(baseUrl, client).create(BoxLoreApiV2::class.java)

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(StreamingJsonConverterFactory.create(contentType, json))
        .build()
}
