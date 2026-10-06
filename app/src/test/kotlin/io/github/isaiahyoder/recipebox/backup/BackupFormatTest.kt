package io.github.isaiahyoder.recipebox.backup

import io.github.isaiahyoder.recipebox.data.TagSource
import io.github.isaiahyoder.recipebox.ingredients.UnitSystem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The backup layout must keep every field older versions wrote, so every saved backup keeps restoring. */
class BackupFormatTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** A format 2 backup with every field, as 0.5.0 wrote it. The data is made up for the test. */
    private val format2 = """
        {"format":2,"appVersion":"0.5.0","exportedAt":100,
         "recipes":[{"id":1,"title":"Test Cake","sourceUrl":"https://example.com/cake","siteName":"Example",
           "description":"A cake.","imageUrl":"https://example.com/cake.jpg","imageFile":null,"imageIsOwn":false,
           "yieldText":"1 cake","servings":8,"prepMinutes":10,"cookMinutes":30,"totalMinutes":40,
           "ingredients":[{"text":"For the cake","isHeader":true},{"text":"2 cups flour","isHeader":false}],
           "steps":[{"text":"Bake.","isHeader":false}],"siteCategories":["Dessert"],"siteCuisines":["American"],
           "siteKeywords":["easy"],"rawJsonLd":"{}","notes":"Good.","favorite":true,"lastScale":1.5,"showUsUnits":true,
           "cardPhotos":["card-1.jpg"],"editedFields":["title"],"createdAt":1,"updatedAt":2}],
         "tags":[{"id":3,"name":"Dessert"}],
         "recipeTags":[{"recipeId":1,"tagId":3,"source":"AUTO","hidden":false}],
         "categories":[{"id":4,"name":"Sweets","position":0,"feederTags":["Dessert"]}],
         "recipeCategories":[{"recipeId":1,"categoryId":4,"source":"MANUAL","hidden":false}],
         "groceryLists":[{"id":5,"name":"Party","hideStaples":true,"createdAt":3,"updatedAt":4,"units":"METRIC"}],
         "groceryListRecipes":[{"listId":5,"recipeId":1,"scale":2.0,"addedAt":5}],
         "groceryManualItems":[{"id":6,"listId":5,"text":"Candles","section":"OTHER","checked":false,"createdAt":6}],
         "groceryLineStates":[{"listId":5,"lineKey":"r:flour","checked":true,"hidden":false,"customText":"Flour"}],
         "sectionOverrides":[{"nameKey":"candle","section":"OTHER"}],
         "themeMode":"DARK"}
    """.trimIndent()

    /** Format 3, as 0.6.0 writes it: format 2 with uids, change times, source kinds, and deletions. */
    private val format3 = format2
        .replace("\"format\":2", "\"format\":3")
        .replace("\"updatedAt\":2}", "\"updatedAt\":2,\"uid\":\"r-1\",\"changedAt\":7,\"sourceKind\":\"card\"}")
        .replace("\"feederTags\":[\"Dessert\"]}", "\"feederTags\":[\"Dessert\"],\"uid\":\"c-4\",\"changedAt\":8}")
        .replace("\"units\":\"METRIC\"}", "\"units\":\"METRIC\",\"uid\":\"g-5\",\"changedAt\":9}")
        .replace("\"section\":\"OTHER\"}]", "\"section\":\"OTHER\",\"changedAt\":10}]")
        .replace("\"themeMode\":\"DARK\"}", "\"themeMode\":\"DARK\",\"deletions\":[{\"uid\":\"r-2\",\"kind\":\"recipe\",\"deletedAt\":11}]}")

    @Test fun aFormat3BackupReadsAndWritesBackUnchanged() {
        val backup = json.decodeFromString<BackupFile>(format3)
        assertEquals("r-2", backup.deletions.single().uid)
        assertEquals(json.parseToJsonElement(format3), json.parseToJsonElement(json.encodeToString(backup)))
    }

    @Test fun aFormat2BackupKeepsEveryFieldWhenWrittenAgain() {
        val backup = json.decodeFromString<BackupFile>(format2)
        val written = json.parseToJsonElement(json.encodeToString(backup)).jsonObject
        assertContains(json.parseToJsonElement(format2).jsonObject, written)
    }

    @Test fun recordsFromOlderBackupsGetNewUidsAndChangeTimes() {
        val backup = json.decodeFromString<BackupFile>(format2)
        val recipe = backup.recipes.single().toEntity()
        assertTrue(recipe.uid.isNotBlank())
        assertEquals(2L, recipe.changedAt)
        assertEquals("A backup without a kind judges it from the card photos", "card", recipe.sourceKind)
        assertEquals(100L, backup.categories.single().toEntity(backupTime = 100).changedAt)
        assertTrue(backup.groceryLists.single().toEntity().uid.isNotBlank())
        val format3Backup = json.decodeFromString<BackupFile>(format3)
        assertEquals("r-1", format3Backup.recipes.single().toEntity().uid)
        assertEquals("c-4", format3Backup.categories.single().toEntity(backupTime = 100).uid)
        assertEquals(format3Backup.recipes.single(), format3Backup.recipes.single().toEntity().toBackup())
    }

    /** Every field in [expected] is in [actual] with the same value. */
    private fun assertContains(expected: JsonElement, actual: JsonElement) {
        when (expected) {
            is JsonObject -> expected.forEach { (key, value) -> assertContains(value, (actual as JsonObject)[key] ?: error("Missing $key")) }
            is JsonArray -> expected.forEachIndexed { index, value -> assertContains(value, (actual as JsonArray)[index]) }
            else -> assertEquals(expected, actual)
        }
    }

    @Test fun backupRecordsBecomeDatabaseRowsAndBack() {
        val backup = json.decodeFromString<BackupFile>(format2)
        val recipe = backup.recipes.single().toEntity()
        assertEquals("2 cups flour", recipe.ingredientText)
        assertTrue(recipe.ingredients.first().isHeader)
        assertEquals(backup.recipes.single(), recipe.toBackup().copy(uid = "", changedAt = 0, sourceKind = ""))
        assertEquals(TagSource.AUTO, backup.recipeTags.single().toEntity().source)
        assertEquals(UnitSystem.METRIC, backup.groceryLists.single().toEntity().units)
        assertEquals(backup.groceryLineStates.single(), backup.groceryLineStates.single().toEntity().toBackup())
    }

    @Test fun aFormat1BackupReadsWithDefaults() {
        val format1 = """
            {"format":1,"appVersion":"0.2.0","exportedAt":100,
             "recipes":[{"id":1,"title":"Old Soup","createdAt":1,"updatedAt":2}],
             "tags":[],"recipeTags":[],"categories":[{"id":4,"name":"Soup","position":0}],
             "recipeCategories":[{"recipeId":1,"categoryId":4}],"groceryLists":[],"groceryListRecipes":[],
             "groceryManualItems":[],"groceryLineStates":[],"sectionOverrides":[]}
        """.trimIndent()
        val backup = json.decodeFromString<BackupFile>(format1)
        val recipe = backup.recipes.single()
        assertTrue(recipe.cardPhotos.isEmpty() && recipe.editedFields.isEmpty())
        assertEquals(TagSource.MANUAL, backup.recipeCategories.single().source)
        assertTrue(backup.categories.single().feederTags.isEmpty())
        assertTrue((json.parseToJsonElement(json.encodeToString(backup)) as JsonObject)["recipes"] != null)
        assertTrue(json.parseToJsonElement(format1).jsonObject["themeMode"] == null)
    }
}
