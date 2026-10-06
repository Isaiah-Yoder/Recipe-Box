package io.github.isaiahyoder.recipebox.backup

import io.github.isaiahyoder.recipebox.data.CategoryEntity
import io.github.isaiahyoder.recipebox.data.GroceryLineStateEntity
import io.github.isaiahyoder.recipebox.data.GroceryListEntity
import io.github.isaiahyoder.recipebox.data.GroceryListRecipeEntity
import io.github.isaiahyoder.recipebox.data.GroceryManualItemEntity
import io.github.isaiahyoder.recipebox.data.RecipeCategoryEntity
import io.github.isaiahyoder.recipebox.data.RecipeEntity
import io.github.isaiahyoder.recipebox.model.RecipeLine
import io.github.isaiahyoder.recipebox.data.RecipeTagEntity
import io.github.isaiahyoder.recipebox.data.SectionOverrideEntity
import io.github.isaiahyoder.recipebox.data.TagEntity
import io.github.isaiahyoder.recipebox.data.TagSource
import io.github.isaiahyoder.recipebox.ingredients.UnitSystem
import kotlinx.serialization.Serializable

/*
 * The backup file's layout, separate from the database's classes.
 *
 * Every backup she has ever saved must keep restoring, so these field names
 * and defaults never change; they are format 2's. The database can rename or
 * add columns freely: only the mappers below change. A new field needs a
 * default so older backups still read, and a layout change raises
 * BackupFile.FORMAT.
 */

@Serializable
data class BackupLine(val text: String, val isHeader: Boolean = false)

@Serializable
data class BackupRecipe(
    val id: Long = 0,
    val title: String,
    val sourceUrl: String? = null,
    val siteName: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val imageFile: String? = null,
    val imageIsOwn: Boolean = false,
    val yieldText: String? = null,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<BackupLine> = emptyList(),
    val steps: List<BackupLine> = emptyList(),
    val siteCategories: List<String> = emptyList(),
    val siteCuisines: List<String> = emptyList(),
    val siteKeywords: List<String> = emptyList(),
    val rawJsonLd: String? = null,
    val notes: String = "",
    val favorite: Boolean = false,
    val lastScale: Double = 1.0,
    val showUsUnits: Boolean = true,
    val cardPhotos: List<String> = emptyList(),
    val editedFields: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupTag(val id: Long = 0, val name: String)

@Serializable
data class BackupRecipeTag(val recipeId: Long, val tagId: Long, val source: TagSource, val hidden: Boolean = false)

@Serializable
data class BackupCategory(val id: Long = 0, val name: String, val position: Int, val feederTags: List<String> = emptyList())

@Serializable
data class BackupRecipeCategory(
    val recipeId: Long,
    val categoryId: Long,
    val source: TagSource = TagSource.MANUAL,
    val hidden: Boolean = false,
)

@Serializable
data class BackupGroceryList(
    val id: Long = 0,
    val name: String,
    val hideStaples: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val units: UnitSystem = UnitSystem.US,
)

@Serializable
data class BackupGroceryListRecipe(val listId: Long, val recipeId: Long, val scale: Double, val addedAt: Long)

@Serializable
data class BackupGroceryManualItem(
    val id: Long = 0,
    val listId: Long,
    val text: String,
    val section: String? = null,
    val checked: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class BackupGroceryLineState(
    val listId: Long,
    val lineKey: String,
    val checked: Boolean = false,
    val hidden: Boolean = false,
    val customText: String? = null,
)

@Serializable
data class BackupSectionOverride(val nameKey: String, val section: String)

// Mappers between the backup layout and the database.

private fun RecipeLine.toBackup() = BackupLine(text, isHeader)
private fun BackupLine.toEntity() = RecipeLine(text, isHeader)

fun RecipeEntity.toBackup() = BackupRecipe(
    id = id, title = title, sourceUrl = sourceUrl, siteName = siteName, description = description,
    imageUrl = imageUrl, imageFile = imageFile, imageIsOwn = imageIsOwn, yieldText = yieldText,
    servings = servings, prepMinutes = prepMinutes, cookMinutes = cookMinutes, totalMinutes = totalMinutes,
    ingredients = ingredients.map { it.toBackup() }, steps = steps.map { it.toBackup() },
    siteCategories = siteCategories, siteCuisines = siteCuisines, siteKeywords = siteKeywords,
    rawJsonLd = rawJsonLd, notes = notes, favorite = favorite, lastScale = lastScale, showUsUnits = showUsUnits,
    cardPhotos = cardPhotos, editedFields = editedFields, createdAt = createdAt, updatedAt = updatedAt,
)

/** The search text isn't in backups; it's rebuilt from the ingredients. */
fun BackupRecipe.toEntity() = RecipeEntity(
    id = id, title = title, sourceUrl = sourceUrl, siteName = siteName, description = description,
    imageUrl = imageUrl, imageFile = imageFile, imageIsOwn = imageIsOwn, yieldText = yieldText,
    servings = servings, prepMinutes = prepMinutes, cookMinutes = cookMinutes, totalMinutes = totalMinutes,
    ingredients = ingredients.map { it.toEntity() }, steps = steps.map { it.toEntity() },
    siteCategories = siteCategories, siteCuisines = siteCuisines, siteKeywords = siteKeywords,
    rawJsonLd = rawJsonLd, notes = notes, favorite = favorite, lastScale = lastScale, showUsUnits = showUsUnits,
    cardPhotos = cardPhotos, editedFields = editedFields, createdAt = createdAt, updatedAt = updatedAt,
).indexed()

fun TagEntity.toBackup() = BackupTag(id, name)
fun BackupTag.toEntity() = TagEntity(id, name)

fun RecipeTagEntity.toBackup() = BackupRecipeTag(recipeId, tagId, source, hidden)
fun BackupRecipeTag.toEntity() = RecipeTagEntity(recipeId, tagId, source, hidden)

fun CategoryEntity.toBackup() = BackupCategory(id, name, position, feederTags)
fun BackupCategory.toEntity() = CategoryEntity(id, name, position, feederTags)

fun RecipeCategoryEntity.toBackup() = BackupRecipeCategory(recipeId, categoryId, source, hidden)
fun BackupRecipeCategory.toEntity() = RecipeCategoryEntity(recipeId, categoryId, source, hidden)

fun GroceryListEntity.toBackup() = BackupGroceryList(id, name, hideStaples, createdAt, updatedAt, units)
fun BackupGroceryList.toEntity() = GroceryListEntity(id, name, hideStaples, createdAt, updatedAt, units)

fun GroceryListRecipeEntity.toBackup() = BackupGroceryListRecipe(listId, recipeId, scale, addedAt)
fun BackupGroceryListRecipe.toEntity() = GroceryListRecipeEntity(listId, recipeId, scale, addedAt)

fun GroceryManualItemEntity.toBackup() = BackupGroceryManualItem(id, listId, text, section, checked, createdAt)
fun BackupGroceryManualItem.toEntity() = GroceryManualItemEntity(id, listId, text, section, checked, createdAt)

fun GroceryLineStateEntity.toBackup() = BackupGroceryLineState(listId, lineKey, checked, hidden, customText)
fun BackupGroceryLineState.toEntity() = GroceryLineStateEntity(listId, lineKey, checked, hidden, customText)

fun SectionOverrideEntity.toBackup() = BackupSectionOverride(nameKey, section)
fun BackupSectionOverride.toEntity() = SectionOverrideEntity(nameKey, section)
