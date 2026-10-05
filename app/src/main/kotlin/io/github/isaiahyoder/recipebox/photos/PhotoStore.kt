package io.github.isaiahyoder.recipebox.photos

import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import kotlin.math.max

/**
 * Keeps recipe photos in the app's private storage.
 *
 * Cover photos are resized to phone width before saving, about 100 to 200 KB
 * each, and can be downloaded again from the recipe's saved image address.
 */
class PhotoStore(
    context: Context,
    private val client: OkHttpClient,
    private val userAgent: String,
    private val folder: File = File(context.filesDir, "photos"),
    /** Loads a photo through a browser when a site refuses the plain download. */
    private val browserImage: (suspend (url: String, maxEdge: Int) -> ByteArray?)? = null,
) {
    init {
        folder.mkdirs()
    }

    private val resolver = context.contentResolver

    fun file(name: String): File = File(folder, name)

    /** The photo file when it exists, so a missing file shows the placeholder instead of nothing. */
    fun existing(name: String?): File? = name?.let(::file)?.takeIf { it.exists() }

    suspend fun downloadCover(imageUrl: String, recipeId: Long): String? {
        val bytes = withContext(Dispatchers.IO) {
            runCatchingCancellable {
                val request = Request.Builder().url(imageUrl).header("User-Agent", userAgent).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body.bytes() else null
                }
            }.getOrNull()
        } ?: browserImage?.let { load -> runCatchingCancellable { load(imageUrl, MAX_EDGE) }.getOrNull() }
            ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val name = "cover-$recipeId.jpg"
                saveResized(bytes, file(name)) ?: return@runCatching null
                name
            }.getOrNull()
        }
    }

    /**
     * Saves a photo she chose or took, such as a recipe card, at a size that
     * keeps handwriting readable. Returns the new file name.
     */
    suspend fun saveOwnPhoto(uri: Uri, recipeId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = "own-$recipeId-${System.currentTimeMillis()}.jpg"
            saveUri(uri, file(name), OWN_PHOTO_EDGE)
            name
        }.getOrNull()
    }

    private fun saveUri(uri: Uri, target: File, maxEdge: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder applies the camera's rotation, so a card photo isn't sideways.
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val edge = max(info.size.width, info.size.height)
                if (edge > maxEdge) {
                    val scale = maxEdge.toFloat() / edge
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
            writeJpeg(bitmap, target)
        } else {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Couldn't open the photo")
            saveResized(bytes, target, maxEdge) ?: error("Couldn't read the photo")
        }
    }

    /**
     * Saves a recipe card photo at a size that keeps handwriting readable for
     * the card readers. Returns the new file name.
     */
    suspend fun saveCardPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val name = "$CARD_PREFIX${System.currentTimeMillis()}-${(1000..9999).random()}.jpg"
            saveUri(uri, file(name), CARD_PHOTO_EDGE)
            name
        }.getOrNull()
    }

    /** Deletes card photos no recipe uses that are over a day old, such as from a scan she didn't save. */
    fun deleteUnusedCardPhotos(used: Set<String>, now: Long = System.currentTimeMillis()) {
        folder.listFiles()
            ?.filter { it.name.startsWith(CARD_PREFIX) && it.name !in used && now - it.lastModified() > DAY_MS }
            ?.forEach { it.delete() }
    }

    fun delete(name: String?) {
        if (name != null) file(name).delete()
    }

    /** Removes every photo except [keep], such as old covers after a restore. */
    fun deleteAllExcept(keep: Set<String>) {
        folder.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    private fun saveResized(bytes: ByteArray, target: File, maxEdge: Int = MAX_EDGE): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = maxEdge.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        writeJpeg(bitmap, target)
        return target
    }

    /** Writes through a temporary file, so a failed write never leaves half a photo. */
    private fun writeJpeg(bitmap: Bitmap, target: File) {
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private companion object {
        const val MAX_EDGE = 1080
        const val OWN_PHOTO_EDGE = 1600
        const val CARD_PHOTO_EDGE = 2048
        const val CARD_PREFIX = "card-"
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
