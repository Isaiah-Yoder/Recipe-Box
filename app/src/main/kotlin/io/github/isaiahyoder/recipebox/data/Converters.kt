package io.github.isaiahyoder.recipebox.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

/** Stores lists as JSON text so a schema change isn't needed to add list items. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun linesToJson(lines: List<RecipeLine>): String = json.encodeToString(lines)

    @TypeConverter
    fun jsonToLines(text: String): List<RecipeLine> = json.decodeFromString(text)

    @TypeConverter
    fun stringsToJson(values: List<String>): String = json.encodeToString(values)

    @TypeConverter
    fun jsonToStrings(text: String): List<String> = json.decodeFromString(text)

    @TypeConverter
    fun tagSourceToText(source: TagSource): String = source.name

    @TypeConverter
    fun textToTagSource(text: String): TagSource = TagSource.valueOf(text)
}
