package io.github.isaiahyoder.recipebox.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A release on GitHub that is newer than the installed app. */
data class AvailableUpdate(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
)

/** Reads GitHub's "latest release" answer and compares version numbers. */
object Releases {
    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String,
        val body: String? = null,
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String,
        val size: Long = 0,
        @SerialName("browser_download_url") val downloadUrl: String,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns the update that [releaseJson] offers, or null when it isn't
     * newer than [installedVersion] or has no APK attached.
     */
    fun newerThan(installedVersion: String, releaseJson: String): AvailableUpdate? {
        val release = json.decodeFromString<Release>(releaseJson)
        if (release.draft || release.prerelease) return null
        val version = release.tagName.removePrefix("v").removePrefix("V")
        if (compare(version, installedVersion) <= 0) return null
        val apk = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        return AvailableUpdate(
            versionName = version,
            notes = release.body.orEmpty().trim(),
            apkUrl = apk.downloadUrl,
            apkSize = apk.size,
        )
    }

    /**
     * Compares versions such as "0.10.0" and "0.9.2" number by number.
     * Text after a dash, such as "-debug", is ignored.
     */
    fun compare(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val difference = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }

    private fun parts(version: String): List<Int> =
        version.substringBefore('-').split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
