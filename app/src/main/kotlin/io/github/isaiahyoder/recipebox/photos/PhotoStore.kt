package io.github.isaiahyoder.recipebox.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
) {
    private val folder = File(context.filesDir, "photos").apply { mkdirs() }

    fun file(name: String): File = File(folder, name)

    suspend fun downloadCover(imageUrl: String, recipeId: Long): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(imageUrl).header("User-Agent", userAgent).build()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                response.body.bytes()
            }
            val name = "cover-$recipeId.jpg"
            saveResized(bytes, file(name)) ?: return@runCatching null
            name
        }.getOrNull()
    }

    fun delete(name: String?) {
        if (name != null) file(name).delete()
    }

    private fun saveResized(bytes: ByteArray, target: File): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else {
            decoded
        }
        val temp = File(target.parentFile, target.name + ".tmp")
        temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }

    private companion object {
        const val MAX_EDGE = 1080
    }
}
