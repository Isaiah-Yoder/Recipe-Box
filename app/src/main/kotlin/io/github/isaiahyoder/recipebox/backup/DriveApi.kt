package io.github.isaiahyoder.recipebox.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Serializable
data class DriveFile(val id: String, val name: String, val createdTime: String? = null, val trashed: Boolean = false)

@Serializable
private data class DriveFileList(val files: List<DriveFile> = emptyList())

class DriveException(val code: Int, message: String) : Exception(message)

/**
 * The few Google Drive REST calls backups need. With the drive.file scope,
 * the app sees only the files it created, never her other Drive files.
 */
class DriveApi(private val client: OkHttpClient, private val token: String) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(query: String, orderBy: String? = null): List<DriveFile> {
        val url = "$FILES".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("fields", "files(id,name,createdTime,trashed)")
            .addQueryParameter("pageSize", "1000")
            .apply { orderBy?.let { addQueryParameter("orderBy", it) } }
            .build()
        return json.decodeFromString<DriveFileList>(call(Request.Builder().url(url).get()).decodeToString()).files
    }

    suspend fun get(id: String): DriveFile? = try {
        val url = "$FILES/$id".toHttpUrl().newBuilder().addQueryParameter("fields", "id,name,trashed").build()
        json.decodeFromString<DriveFile>(call(Request.Builder().url(url).get()).decodeToString())
    } catch (e: DriveException) {
        if (e.code == 404) null else throw e
    }

    suspend fun createFolder(name: String, parentId: String?): String {
        val metadata = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
            parentId?.let { put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(it)) }) }
        }
        val body = metadata.toString().toRequestBody(JSON_MIME)
        return json.decodeFromString<DriveFile>(call(Request.Builder().url("$FILES?fields=id,name").post(body)).decodeToString()).id
    }

    suspend fun upload(name: String, mimeType: String, bytes: ByteArray, parentId: String): String {
        val metadata = buildJsonObject {
            put("name", name)
            put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parentId)) })
        }
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(JSON_MIME))
            .addPart(bytes.toRequestBody(mimeType.toMediaType()))
            .build()
        val request = Request.Builder().url("$UPLOAD?uploadType=multipart&fields=id,name").post(body)
        return json.decodeFromString<DriveFile>(call(request).decodeToString()).id
    }

    suspend fun download(id: String): ByteArray = call(Request.Builder().url("$FILES/$id?alt=media").get())

    suspend fun delete(id: String) {
        call(Request.Builder().url("$FILES/$id").delete())
    }

    private suspend fun call(builder: Request.Builder): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(builder.header("Authorization", "Bearer $token").build()).execute().use { response: Response ->
            val bytes = response.body.bytes()
            if (!response.isSuccessful) throw DriveException(response.code, "Google Drive answered with HTTP ${response.code}")
            bytes
        }
    }

    companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val FILES = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"
        private val JSON_MIME = "application/json; charset=UTF-8".toMediaType()

        /** Quotes a value for a Drive search query. */
        fun quote(value: String) = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
    }
}
