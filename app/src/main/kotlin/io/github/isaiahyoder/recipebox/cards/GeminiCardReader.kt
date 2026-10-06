package io.github.isaiahyoder.recipebox.cards

import io.github.isaiahyoder.recipebox.AppStrings
import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

/** Why a reader couldn't read the card, in words she can act on. */
class CardReadException(
    message: String,
    /** True when another Gemini model might still work, such as when one model's free limit is used up. */
    val tryNextModel: Boolean = false,
    /** True when the key itself was refused, so she should check it in Settings. */
    val badKey: Boolean = false,
    /** True when Gemini was overloaded, which usually clears within seconds. */
    val busy: Boolean = false,
) : Exception(message)

/**
 * Reads card photos with Google's Gemini API, using the key she entered.
 *
 * The photos and the prompt from assets/cards go in one request, and Gemini
 * answers in the JSON layout of assets/cards/schema.json. On the free tier,
 * Google may use what's sent to improve its products.
 */
class GeminiCardReader(http: OkHttpClient, private val assets: CardAssets, private val strings: AppStrings) {
    private val client = http.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(key: String, photos: List<File>): CardRecipe = withContext(Dispatchers.IO) {
        val images = photos.map { Base64.getEncoder().encodeToString(it.readBytes()) }
        var last: CardReadException? = null
        // When every model is only busy, one more round after a short wait usually succeeds.
        repeat(2) { round ->
            if (round > 0) {
                if (last?.busy != true) throw last ?: CardReadException("Gemini couldn't read the card.")
                delay(BUSY_WAIT_MS)
            }
            var allBusy = true
            for (model in MODELS) {
                try {
                    return@withContext request(model, key, images)
                } catch (error: CardReadException) {
                    if (!error.tryNextModel) throw error
                    allBusy = allBusy && error.busy
                    last = error
                }
            }
            if (!allBusy) last = CardReadException(last?.message ?: "Gemini couldn't read the card.")
        }
        throw last ?: CardReadException("Gemini couldn't read the card.")
    }

    /** Checks that [key] works without sending any photos. */
    suspend fun checkKey(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val request = Request.Builder().url("$BASE/models?pageSize=1").header("x-goog-api-key", key).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw failure(response.code, response.body.string())
            }
        }.recoverCatching { error ->
            throw if (error is IOException) CardReadException("Couldn't reach Gemini. Check your internet connection.") else error
        }
    }

    private fun request(model: String, key: String, images: List<String>): CardRecipe {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", assets.prompt) }
                        for (image in images) {
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", image)
                                }
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("responseJsonSchema", json.parseToJsonElement(assets.schema))
            }
        }
        val request = Request.Builder()
            .url("$BASE/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val text = try {
            client.newCall(request).execute().use { response ->
                val answer = response.body.string()
                if (!response.isSuccessful) throw failure(response.code, answer)
                answerText(json.parseToJsonElement(answer))
            }
        } catch (error: IOException) {
            throw CardReadException("Couldn't reach Gemini. Check your internet connection.")
        }
        val recipe = text?.let(CardJson::parse)
        if (recipe == null || recipe.isEmpty) {
            throw CardReadException("Gemini couldn't find a recipe in the photos.", tryNextModel = true)
        }
        return recipe
    }

    /** Joins the answer's text parts, skipping the model's thinking. */
    private fun answerText(answer: JsonElement): String? {
        val parts = answer.jsonObject["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return null
        return parts.mapNotNull { part ->
            val obj = part as? JsonObject ?: return@mapNotNull null
            if (obj["thought"]?.jsonPrimitive?.booleanOrNull == true) null else obj["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("").ifBlank { null }
    }

    private fun failure(code: Int, body: String): CardReadException {
        val badKey = code == 401 || code == 403 || "API_KEY_INVALID" in body || "API key not valid" in body
        return when {
            badKey -> CardReadException("Gemini didn't accept the key. Check it in Settings.", badKey = true)
            code == 429 -> CardReadException("Gemini's free limit is used up for now. Try again later.", tryNextModel = true)
            code == 404 -> CardReadException("Gemini's reading model isn't available.", tryNextModel = true)
            code >= 500 -> CardReadException("Gemini is busy right now. Try again in a few minutes.", tryNextModel = true, busy = true)
            else -> CardReadException("Gemini couldn't read the card (error $code).", tryNextModel = true)
        }
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        private const val BUSY_WAIT_MS = 8_000L

        /**
         * Tried in order. Each model has its own free limit, so another model
         * can still answer when one is busy or used up. Pinned names don't
         * change behavior without an app update, unlike "-latest" aliases.
         * On test cards in October 2026, all three read handwriting correctly;
         * 3.8 Flash followed the formatting rules best, Flash-Lite was fastest
         * at 2 to 7 seconds, and 3.5 Flash was slowest at up to a minute.
         */
        val MODELS = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.5-flash")
    }
}

/** The prompt and answer layout shared by the AI readers and the test script. */
class CardAssets(private val open: (String) -> String) {
    val prompt: String by lazy { open("cards/prompt.txt").trim() }
    val schema: String by lazy { open("cards/schema.json") }
}
