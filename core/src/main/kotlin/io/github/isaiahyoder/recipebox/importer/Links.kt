package io.github.isaiahyoder.recipebox.importer

import java.net.URI

object Links {
    private val urlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val trackingParameters = Regex("""^(utm_\w+|fbclid|gclid|mc_cid|mc_eid|ref|ref_src)$""", RegexOption.IGNORE_CASE)

    /**
     * Finds the first web address in shared text. Apps often share a page
     * title followed by its address, so the text isn't always a bare link.
     */
    fun findUrl(text: String): String? =
        urlPattern.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')

    /** Finds every web address in pasted or shared text, in order, without repeats. */
    fun findAllUrls(text: String): List<String> =
        urlPattern.findAll(text)
            .map { it.value.trimEnd('.', ',', ')', ']', '!', '?', ';', ':') }
            .map(::normalize)
            .distinct()
            .toList()

    /** Removes tracking parameters and fragments so the same page is recognized as a duplicate. */
    fun normalize(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url.trim()
        val query = uri.rawQuery?.split('&')
            ?.filter { it.isNotBlank() && !trackingParameters.matches(it.substringBefore('=')) }
            ?.joinToString("&")
            ?.ifBlank { null }
        val scheme = uri.scheme?.lowercase() ?: "https"
        val host = uri.host?.lowercase() ?: return url.trim()
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.rawPath?.ifBlank { "/" } ?: "/"
        return "$scheme://$host$port$path" + (query?.let { "?$it" } ?: "")
    }
}
