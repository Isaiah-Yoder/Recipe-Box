package io.github.isaiahyoder.recipebox.importer

import android.annotation.SuppressLint
import android.content.Context
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

    private suspend fun evaluate(webView: WebView, script: String): String =
        suspendCancellableCoroutine { continuation ->
            webView.evaluateJavascript(script) { result -> continuation.resume(result ?: "null") }
        }
}
