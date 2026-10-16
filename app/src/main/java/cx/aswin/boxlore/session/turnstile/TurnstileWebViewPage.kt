package cx.aswin.boxlore.session.turnstile

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI

/**
 * The Turnstile page in a locked-down WebView, set up as Cloudflare's mobile guide requires
 * (JavaScript, DOM storage, cookies including Turnstile's frame, the default user agent) but with no
 * file or content access, no new windows, top-level navigation limited to the API host, Turnstile and
 * `about:blank` / `about:srcdoc`, and a bridge that only the API host's own page can post to.
 */
internal object TurnstileWebViewPage : TurnstilePageFactory {
    @SuppressLint("SetJavaScriptEnabled") // Turnstile is JavaScript; the page is our own, on the API host.
    override fun create(context: Context, pageUrl: String, onMessage: (String?) -> Unit, onFailed: (String) -> Unit): View {
        val webView = WebView(context)
        val origin = TurnstileBridge.origin(pageUrl)
        if (origin == null || !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            onFailed("webview_unsupported")
            return webView
        }
        val apiHost = runCatching { URI(pageUrl).host }.getOrNull()
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
        }
        // Turnstile's challenge runs in a challenges.cloudflare.com frame and needs its cookies.
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        WebViewCompat.addWebMessageListener(webView, TurnstileBridge.BRIDGE_NAME, setOf(origin)) { _, message, _, isMainFrame, _ ->
            if (isMainFrame) onMessage(message.data)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !TurnstileBridge.allowsNavigation(request.url.toString(), apiHost)

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) onFailed("page_error_${error.errorCode}")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame) onFailed("page_http_${errorResponse.statusCode}")
            }
        }
        webView.loadUrl(pageUrl)
        return webView
    }

    fun destroy(view: View) {
        (view as? WebView)?.apply {
            stopLoading()
            destroy()
        }
    }
}
