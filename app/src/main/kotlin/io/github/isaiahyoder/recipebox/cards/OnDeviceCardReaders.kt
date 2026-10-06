package io.github.isaiahyoder.recipebox.cards

import io.github.isaiahyoder.recipebox.AppStrings
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.content
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

enum class OnDeviceAiStatus { CHECKING, READY, DOWNLOADING, UNAVAILABLE }

/**
 * Reads card photos with Gemini Nano, the AI model built into some phones,
 * such as the Galaxy S26. Nothing leaves the phone. Phones without it,
 * including the emulator, report [OnDeviceAiStatus.UNAVAILABLE].
 */
class NanoCardReader(
    private val assets: CardAssets,
    private val scope: CoroutineScope,
    private val strings: AppStrings,
) : RecipeCardReader {
    override val kind = CardReaderKind.ON_DEVICE_AI

    override fun isAvailable() = status.value == OnDeviceAiStatus.READY

    private val model: GenerativeModel? by lazy { runCatching { Generation.getClient() }.getOrNull() }

    private val _status = MutableStateFlow(OnDeviceAiStatus.CHECKING)
    val status: StateFlow<OnDeviceAiStatus> = _status.asStateFlow()

    /** Checks the phone and starts the model download when it's needed. */
    fun refresh() {
        scope.launch {
            val current = runCatchingCancellable { model?.checkStatus() }.getOrNull()
            _status.value = when (current) {
                FeatureStatus.AVAILABLE -> OnDeviceAiStatus.READY
                FeatureStatus.DOWNLOADING -> OnDeviceAiStatus.DOWNLOADING
                FeatureStatus.DOWNLOADABLE -> {
                    download()
                    OnDeviceAiStatus.DOWNLOADING
                }
                else -> OnDeviceAiStatus.UNAVAILABLE
            }
        }
    }

    private fun download() {
        scope.launch {
            val succeeded = runCatchingCancellable { model?.download()?.collect {} }.isSuccess
            _status.value = if (succeeded && runCatchingCancellable { model?.checkStatus() }.getOrNull() == FeatureStatus.AVAILABLE) {
                OnDeviceAiStatus.READY
            } else {
                OnDeviceAiStatus.UNAVAILABLE
            }
        }
    }

    override suspend fun read(photos: List<File>): CardRecipe {
        val model = model ?: throw CardReadException(strings.get(R.string.cards_on_device_missing))
        if (runCatchingCancellable { model.checkStatus() }.getOrNull() != FeatureStatus.AVAILABLE) {
            throw CardReadException(strings.get(R.string.cards_on_device_not_ready))
        }
        // Smaller images keep the request within the on-device model's limit.
        val bitmaps = withContext(Dispatchers.IO) { photos.mapNotNull { decode(it, NANO_EDGE) } }
        val request = generateContentRequest(
            content {
                bitmaps.forEach { image(it) }
                text(assets.prompt + "\n\n" + NANO_FORMAT)
            }
        ) {
            temperature = 0.1f
            maxOutputTokens = 2048
        }
        val text = runCatchingCancellable { model.generateContent(request).candidates.firstOrNull()?.text }
            .getOrElse { throw CardReadException(strings.get(R.string.cards_on_device_failed)) }
        val recipe = text?.let(CardJson::parse)
        if (recipe == null || recipe.isEmpty) throw CardReadException(strings.get(R.string.cards_on_device_no_recipe))
        return recipe
    }

    private companion object {
        const val NANO_EDGE = 1024

        /** The on-device model has no answer schema, so the prompt describes the layout. */
        const val NANO_FORMAT = "Answer with only a JSON object with the keys title, servings, prepTime, cookTime, " +
            "ingredients, steps, notes, course, and cuisine. ingredients and steps are lists of objects with the keys " +
            "text and isHeading. course is one of Main Dish, Side Dish, Dessert, Breakfast, Appetizer, Snack, Bread, " +
            "Soup, Salad, Sauce, or Drink, or an empty string. cuisine is a cuisine such as Italian or Mexican, or an " +
            "empty string."
    }
}

/**
 * Reads the text on card photos with ML Kit text recognition and sorts it
 * with [CardTextParser]. It works on any phone with Google Play services and
 * reads printed cards well, but often misreads handwriting.
 */
class TextCardReader(private val context: Context) : RecipeCardReader {
    override val kind = CardReaderKind.TEXT_RECOGNITION

    override fun isAvailable() = true

    override suspend fun read(photos: List<File>): CardRecipe {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val pages = photos.map { file ->
                val text = recognizer.process(InputImage.fromFilePath(context, Uri.fromFile(file))).await()
                text.textBlocks.flatMap { block -> block.lines.map { it.text } }
            }
            val recipe = CardTextParser.parse(pages)
            if (recipe.isEmpty) throw CardReadException(context.getString(R.string.cards_text_no_writing))
            return recipe
        } catch (error: CardReadException) {
            throw error
        } catch (error: Exception) {
            throw CardReadException(context.getString(R.string.cards_text_failed))
        } finally {
            recognizer.close()
        }
    }
}

internal fun decode(file: File, maxEdge: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val scale = maxEdge.toFloat() / max(decoded.width, decoded.height)
    return if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
    } else {
        decoded
    }
}
