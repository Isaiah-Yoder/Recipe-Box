package io.github.isaiahyoder.recipebox.importer

import io.github.isaiahyoder.recipebox.AppStrings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed interface FetchResult {
    data class Page(val html: String, val finalUrl: String) : FetchResult
    data class Failed(val reason: String) : FetchResult
}

/**
 * Downloads a page the way the phone's browser would.
 *
 * Sites such as Allrecipes sometimes refuse a request (HTTP 403 or 429) and
 * accept the same request a little later, so refusals are retried with a
 * growing pause.
 */
class PageFetcher(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val strings: AppStrings,
    private val retryDelaysMs: List<Long> = listOf(3_000, 10_000),
) {
    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        var lastReason = "No response"
        for (attempt in 0..retryDelaysMs.size) {
            if (attempt > 0) delay(retryDelaysMs[attempt - 1])
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return@withContext FetchResult.Page(response.body.string(), response.request.url.toString())
                    }
                    lastReason = "The site answered with HTTP ${response.code}"
                    if (response.code !in retryableCodes) return@withContext FetchResult.Failed(lastReason)
                }
            } catch (e: IOException) {
                lastReason = e.message ?: "Network error"
            }
            Log.i("RecipeImport", "Attempt ${attempt + 1}: $lastReason")
        }
        FetchResult.Failed(lastReason)
    }

    private val retryableCodes = setOf(403, 408, 429, 500, 502, 503, 504)
}
