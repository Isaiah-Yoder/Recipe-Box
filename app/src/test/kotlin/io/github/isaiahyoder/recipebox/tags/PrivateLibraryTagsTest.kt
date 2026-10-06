package io.github.isaiahyoder.recipebox.tags

import io.github.isaiahyoder.recipebox.backup.toEntity
import io.github.isaiahyoder.recipebox.backup.BackupFile
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/**
 * Compares automatic tags with the categories she chose by hand, using
 * backups saved in the ignored testdata/private/backups folder. It writes a
 * report there for tuning the rules and skips itself without the backups.
 */
class PrivateLibraryTagsTest {
    /** Her category names and the tags that should feed them. */
    private val feeders = mapOf(
        "Dessert" to setOf(AutoTagger.DESSERT),
        "Dinner" to setOf(AutoTagger.MAIN_DISH, AutoTagger.SOUP),
        "Breakfast" to setOf(AutoTagger.BREAKFAST),
        "Breads" to setOf(AutoTagger.BREAD),
        "Sauces" to setOf(AutoTagger.SAUCE),
        "Drinks" to setOf(AutoTagger.DRINK),
        "Soup" to setOf(AutoTagger.SOUP),
        "Dough" to setOf(AutoTagger.DOUGH),
        "Slow Cooker" to setOf("Slow Cooker"),
        "Thanksgiving" to setOf(AutoTagger.THANKSGIVING),
    )

    @Test fun compareWithHerCategories() {
        val folder = listOf(File("testdata/private/backups"), File("../testdata/private/backups")).firstOrNull { it.isDirectory }
        val zip = folder?.listFiles { file -> file.extension == "zip" }?.maxByOrNull { it.lastModified() }
        assumeTrue("No private backup", zip != null)
        val json = Json { ignoreUnknownKeys = true }
        val backup = ZipFile(zip!!).use { file ->
            json.decodeFromString<BackupFile>(file.getInputStream(file.getEntry("backup.json")).readBytes().decodeToString())
        }
        val categoryNames = backup.categories.associate { it.id to it.name }
        val chosen = backup.recipeCategories.groupBy({ it.recipeId }, { categoryNames.getValue(it.categoryId) })
        val report = StringBuilder()
        val results = backup.recipes.associate { it.id to AutoTagger.tags(it.toEntity().toTaggable()) }

        for ((category, tags) in feeders) {
            val hers = backup.recipes.filter { category in chosen[it.id].orEmpty() }.map { it.id }.toSet()
            val ours = results.filter { (_, result) -> (result.tags + result.suggestions).any { it in tags } }.keys
            val title = { id: Long -> backup.recipes.first { it.id == id }.title }
            report.appendLine("## $category: hers ${hers.size}, ours ${ours.size}, both ${(hers intersect ours).size}")
            (hers - ours).forEach { report.appendLine("  missed: ${title(it)}") }
            (ours - hers).forEach { report.appendLine("  extra:  ${title(it)}") }
        }
        report.appendLine()
        for (recipe in backup.recipes) {
            val result = results.getValue(recipe.id)
            report.appendLine("${recipe.title}: ${result.tags.joinToString()}" + if (result.suggestions.isEmpty()) "" else " | suggest ${result.suggestions.joinToString()}")
        }
        File(folder, "tag-report.txt").writeText(report.toString())
    }
}
