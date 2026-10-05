package io.github.isaiahyoder.recipebox.importer

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume

/**
 * Loads a page in an invisible browser and returns its HTML.
 *
 * This is the fallback for sites whose bot checks refuse a plain download:
 * a real browser engine runs those checks the way her browser does. It also
 * shares the app's browser cookies, so a site she signs in to can work.
 */
class WebViewPageLoader(private val context: Context) {
    private val recipeDataPresent = """
        (document.querySelectorAll('script[type*="ld+json"]').length +
         document.querySelectorAll('[itemtype*="schema.org/Recipe"]').length) > 0
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun load(url: String, timeoutMs: Long = 30_000): String? = withContext(Dispatchers.Main) {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
        try {
            webView.loadUrl(url)
            withTimeoutOrNull(timeoutMs) {
                var html: String? = null
                while (html == null) {
                    delay(1_500)
                    if (evaluate(webView, recipeDataPresent) == "true") {
                        // Give scripts that add data late a moment to finish.
                        delay(500)
                        html = Json.decodeFromString<String>(evaluate(webView, "document.documentElement.outerHTML"))
                    }
                }
                html
            }
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    /**
     * Loads a photo the way a browser does and returns it as JPEG bytes, at
     * most [maxEdge] pixels on the long side. Some sites refuse the app's own
     * download of a photo they show any browser.
     */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun loadImage(url: String, maxEdge: Int, timeoutMs: Long = 20_000): ByteArray? = withContext(Dispatchers.Main) {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
        }
        try {
            webView.loadUrl(url)
            val dataUrl = withTimeoutOrNull(timeoutMs) {
                var data: String? = null
                while (data == null) {
                    delay(500)
                    data = Json.decodeFromString<String?>(evaluate(webView, imageAsJpeg(maxEdge)))
                }
                data
            }
            dataUrl?.substringAfter(',')?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // The browser shows an image address as a page holding only that image, from the
    // image's own site, so a canvas can copy it.
    private fun imageAsJpeg(maxEdge: Int) = """
        (function() {
          var img = document.images.length == 1 ? document.images[0] : null;
          if (!img || !img.complete || img.naturalWidth == 0) return null;
          var scale = Math.min(1, $maxEdge / Math.max(img.naturalWidth, img.naturalHeight));
          var canvas = document.createElement('canvas');
          canvas.width = Math.round(img.naturalWidth * scale);
          canvas.height = Math.round(img.naturalHeight * scale);
          canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
          try { return canvas.toDataURL('image/jpeg', 0.9); } catch (e) { return null; }
        })()
    """.trimIndent()

    private suspend fun evaluate(webView: WebView, script: String): String =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result -> continuation.resume(result ?: "null") }
        }
}
