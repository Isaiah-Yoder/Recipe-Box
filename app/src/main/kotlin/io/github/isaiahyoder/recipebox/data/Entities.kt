package io.github.isaiahyoder.recipebox.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** One ingredient or step line. A header line names a group, such as "For the sauce". */
@Serializable
data class RecipeLine(
    val text: String,
    val isHeader: Boolean = false,
)

@Serializable
@Entity(
    tableName = "recipes",
    indices = [Index("sourceUrl")],
)
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val sourceUrl: String? = null,
    val siteName: String? = null,
    val description: String? = null,
    /** Address the cover photo was downloaded from. Backups keep this, not the image. */
    val imageUrl: String? = null,
    /** File name of the downloaded or taken photo inside the app's photo folder. */
    val imageFile: String? = null,
    /** True when the photo was taken or chosen by the user and can't be downloaded again. */
    val imageIsOwn: Boolean = false,
    val yieldText: String? = null,
    val servings: Int? = null,
    val prepMinutes: Int? = null,
    val cookMinutes: Int? = null,
    val totalMinutes: Int? = null,
    val ingredients: List<RecipeLine> = emptyList(),
    val steps: List<RecipeLine> = emptyList(),
    /** The site's own category, cuisine, and keyword data, kept for re-tagging. */
    val siteCategories: List<String> = emptyList(),
    val siteCuisines: List<String> = emptyList(),
    val siteKeywords: List<String> = emptyList(),
    /** Raw structured data from the page, so a later parser can re-read it offline. */
    val rawJsonLd: String? = null,
    val notes: String = "",
    val favorite: Boolean = false,
    /** Last scale chosen on the recipe screen; 1.0 is the recipe as written. */
    val lastScale: Double = 1.0,
    /** Whether the recipe screen shows metric amounts in US units. */
    val showUsUnits: Boolean = true,
    /**
     * File names of the recipe card photos it was read from, front first. They
     * can't be downloaded again, so backups include them.
     */
    @ColumnInfo(defaultValue = "[]")
    val cardPhotos: List<String> = emptyList(),
    /**
     * Parts she changed in the editor, from [EditedField]. A refresh from the
     * website leaves these parts alone and updates the rest.
     */
    @ColumnInfo(defaultValue = "[]")
    val editedFields: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * The ingredient lines as plain text, for search. The DAO fills it on every
     * write, so search never matches the JSON that stores [ingredients]. It's
     * rebuilt from the ingredients, so backups leave it out.
     */
    @kotlinx.serialization.Transient
    @ColumnInfo(defaultValue = "")
    val ingredientText: String = "",
) {
    /** This recipe with [ingredientText] matching its ingredients. */
    fun indexed(): RecipeEntity = copy(ingredientText = ingredients.filterNot { it.isHeader }.joinToString("\n") { it.text })
}

@Serializable
@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

/**
 * Where a tag or category membership came from. AUTO comes from the rules or
 * a category's feeder tags, MANUAL from her, and SUGGESTED is a guess she
 * hasn't confirmed, which doesn't count as a tag until she does.
 */
@Serializable
enum class TagSource { AUTO, MANUAL, SUGGESTED }

/** The parts of a recipe the editor tracks, so a refresh keeps her changes. */
object EditedField {
    const val TITLE = "title"
    const val SERVINGS = "servings"
    const val TIMES = "times"
    const val INGREDIENTS = "ingredients"
    const val STEPS = "steps"
    const val SOURCE = "source"
}

@Serializable
@Entity(
    tableName = "recipe_tags",
    primaryKeys = ["recipeId", "tagId"],
    foreignKeys = [
        ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class RecipeTagEntity(
    val recipeId: Long,
    val tagId: Long,
    val source: TagSource,
    /** An automatic tag the user removed. It stays hidden when tags are recomputed. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

@Serializable
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val position: Int,
    /** Tag names that add recipes to this category automatically, now and in the future. */
    @ColumnInfo(defaultValue = "[]") val feederTags: List<String> = emptyList(),
)

@Serializable
@Entity(
    tableName = "recipe_categories",
    primaryKeys = ["recipeId", "categoryId"],
    foreignKeys = [
        ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("categoryId")],
)
data class RecipeCategoryEntity(
    val recipeId: Long,
    val categoryId: Long,
    /** MANUAL when she added it, AUTO when a feeder tag did. */
    @ColumnInfo(defaultValue = "MANUAL") val source: TagSource = TagSource.MANUAL,
    /** She took the recipe out of the category, so feeder tags don't add it back. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

/**
 * The parts of a recipe's web page that the reader uses: its structured data
 * and its recipe card. Kept so later reading fixes can run again on the phone
 * without downloading every page. It isn't in backups; it can be downloaded again.
 */
@Entity(
    tableName = "recipe_pages",
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
)
data class RecipePageEntity(
    @PrimaryKey val recipeId: Long,
    val html: String,
    val savedAt: Long,
)
