package io.github.isaiahyoder.recipebox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.isaiahyoder.recipebox.tags.TagResult
import kotlinx.coroutines.flow.Flow

/** A recipe row for the library list, without the ingredient and step text. */
data class RecipeSummary(
    val id: Long,
    val title: String,
    val siteName: String?,
    val imageFile: String?,
    val cardPhotos: List<String>,
    val totalMinutes: Int?,
    val favorite: Boolean,
    val tagNames: String?,
)

/** A recipe and the name of one of its tags or suggestions. */
data class RecipeTagName(val recipeId: Long, val tag: String)

/** One recipe's card photo file names. */
data class CardPhotoNames(val cardPhotos: List<String>)

/** The category id that lists recipes in no category. */
const val UNCATEGORIZED = -1L

@Dao
interface RecipeDao {
    @Query(
        """
        SELECT r.id, r.title, r.siteName, r.imageFile, r.cardPhotos, r.totalMinutes, r.favorite,
            (SELECT GROUP_CONCAT(t.name, '|') FROM recipe_tags rt
                JOIN tags t ON t.id = rt.tagId
                WHERE rt.recipeId = r.id AND rt.hidden = 0 AND rt.source != 'SUGGESTED') AS tagNames
        FROM recipes r
        WHERE (:query = ''
            OR r.title LIKE '%' || :query || '%'
            OR r.ingredients LIKE '%' || :query || '%'
            OR EXISTS (SELECT 1 FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
                WHERE rt.recipeId = r.id AND rt.hidden = 0 AND rt.source != 'SUGGESTED'
                AND t.name LIKE '%' || :query || '%'))
          AND (:categoryId = 0
            OR (:categoryId = $UNCATEGORIZED AND NOT EXISTS (SELECT 1 FROM recipe_categories rc
                WHERE rc.recipeId = r.id AND rc.hidden = 0))
            OR EXISTS (SELECT 1 FROM recipe_categories rc
                WHERE rc.recipeId = r.id AND rc.categoryId = :categoryId AND rc.hidden = 0))
          AND (:tag = '' OR EXISTS (SELECT 1 FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
                WHERE rt.recipeId = r.id AND rt.hidden = 0 AND rt.source != 'SUGGESTED' AND t.name = :tag))
          AND (:favoritesOnly = 0 OR r.favorite = 1)
        ORDER BY r.favorite DESC, r.updatedAt DESC
        """
    )
    fun observeSummaries(
        query: String,
        categoryId: Long = 0,
        tag: String = "",
        favoritesOnly: Boolean = false,
    ): Flow<List<RecipeSummary>>

    /** Tag names on at least one recipe, for filters and suggestions. */
    @Query(
        """
        SELECT DISTINCT t.name FROM tags t JOIN recipe_tags rt ON rt.tagId = t.id
        WHERE rt.hidden = 0 AND rt.source != 'SUGGESTED' ORDER BY t.name COLLATE NOCASE
        """
    )
    fun observeTagNamesInUse(): Flow<List<String>>

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeRecipe(id: Long): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipe(id: Long): RecipeEntity?

    @Query("SELECT id FROM recipes WHERE sourceUrl = :url LIMIT 1")
    suspend fun findIdBySourceUrl(url: String): Long?

    @Insert
    suspend fun insert(recipe: RecipeEntity): Long

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Query("UPDATE recipes SET lastScale = :scale WHERE id = :id")
    suspend fun setLastScale(id: Long, scale: Double)

    @Query("UPDATE recipes SET showUsUnits = :show WHERE id = :id")
    suspend fun setShowUsUnits(id: Long, show: Boolean)

    @Query("UPDATE recipes SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("SELECT cardPhotos FROM recipes WHERE cardPhotos != '[]'")
    suspend fun allCardPhotos(): List<CardPhotoNames>

    @Query("UPDATE recipes SET imageFile = :imageFile WHERE id = :id")
    suspend fun setImageFile(id: Long, imageFile: String?)

    @Query("UPDATE recipes SET imageUrl = :imageUrl, imageFile = :imageFile WHERE id = :id")
    suspend fun setImage(id: Long, imageUrl: String, imageFile: String)

    @Query("SELECT * FROM recipes WHERE imageFile IS NOT NULL AND imageIsOwn = 0")
    suspend fun recipesWithCover(): List<RecipeEntity>

    /** Recipes whose downloadable cover photo isn't on the phone, such as after a restore. */
    @Query("SELECT * FROM recipes WHERE imageFile IS NULL AND imageUrl IS NOT NULL AND imageIsOwn = 0")
    suspend fun recipesMissingCover(): List<RecipeEntity>

    @Query("SELECT COUNT(*) FROM recipes WHERE imageFile IS NULL AND imageUrl IS NOT NULL AND imageIsOwn = 0")
    fun observeMissingCoverCount(): Flow<Int>

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: Long)

    // Tags

    @Query(
        """
        SELECT t.name FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
        WHERE rt.recipeId = :recipeId AND rt.hidden = 0 AND rt.source != 'SUGGESTED' ORDER BY t.name
        """
    )
    fun observeTagNames(recipeId: Long): Flow<List<String>>

    /** Guessed tags she hasn't confirmed or dismissed, such as an occasion. */
    @Query(
        """
        SELECT t.name FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
        WHERE rt.recipeId = :recipeId AND rt.hidden = 0 AND rt.source = 'SUGGESTED' ORDER BY t.name
        """
    )
    fun observeSuggestedTags(recipeId: Long): Flow<List<String>>

    /** Every unconfirmed suggestion, for categories to offer. */
    @Query(
        """
        SELECT rt.recipeId AS recipeId, t.name AS tag FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
        WHERE rt.hidden = 0 AND rt.source = 'SUGGESTED'
        """
    )
    fun observeSuggestions(): Flow<List<RecipeTagName>>

    /** Every visible, confirmed tag on every recipe. */
    @Query(
        """
        SELECT rt.recipeId AS recipeId, t.name AS tag FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
        WHERE rt.hidden = 0 AND rt.source != 'SUGGESTED'
        """
    )
    fun observeConfirmedTags(): Flow<List<RecipeTagName>>

    @Query("SELECT id FROM tags WHERE name = :name")
    suspend fun findTagId(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM recipe_tags WHERE recipeId = :recipeId")
    suspend fun getRecipeTags(recipeId: Long): List<RecipeTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipeTag(link: RecipeTagEntity)

    @Query("DELETE FROM recipe_tags WHERE recipeId = :recipeId AND source IN ('AUTO', 'SUGGESTED') AND hidden = 0")
    suspend fun clearVisibleAutoTags(recipeId: Long)

    /**
     * Replaces a recipe's automatic tags and suggestions. Tags she removed or
     * dismissed stay hidden, and tags she added or confirmed are left alone.
     */
    @Transaction
    suspend fun replaceAutoTags(recipeId: Long, result: TagResult) {
        val existing = getRecipeTags(recipeId).associateBy { it.tagId }
        clearVisibleAutoTags(recipeId)
        for ((names, source) in listOf(result.tags to TagSource.AUTO, result.suggestions to TagSource.SUGGESTED)) {
            for (name in names) {
                val tagId = findTagId(name) ?: insertTag(TagEntity(name = name))
                val link = existing[tagId]
                if (link == null || (link.source != TagSource.MANUAL && !link.hidden)) {
                    upsertRecipeTag(RecipeTagEntity(recipeId, tagId, source))
                }
            }
        }
    }

    /** Confirms a suggested tag, which then counts like a tag she added. */
    @Transaction
    suspend fun acceptSuggestion(recipeId: Long, name: String) = addManualTag(recipeId, name)

    /** Adds a tag she chose. It becomes hers, so recomputing automatic tags never removes it. */
    @Transaction
    suspend fun addManualTag(recipeId: Long, name: String) {
        val tagId = findTagId(name) ?: insertTag(TagEntity(name = name))
        upsertRecipeTag(RecipeTagEntity(recipeId, tagId, TagSource.MANUAL))
    }

    /**
     * Removes a tag from a recipe. The link is hidden rather than deleted, so
     * recomputing automatic tags never brings back a tag she removed, even
     * when the rules would produce it. Adding the tag again shows it again.
     */
    @Transaction
    suspend fun removeTag(recipeId: Long, name: String) {
        val tagId = findTagId(name) ?: return
        val link = getRecipeTags(recipeId).firstOrNull { it.tagId == tagId } ?: return
        upsertRecipeTag(link.copy(hidden = true))
    }

    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipes(): List<RecipeEntity>

    /** Deletes tag names that no recipe uses any more, including hidden links. */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM recipe_tags)")
    suspend fun deleteUnusedTags()

    // Categories

    @Query("SELECT * FROM categories ORDER BY position, name COLLATE NOCASE")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY position, name COLLATE NOCASE")
    suspend fun getCategories(): List<CategoryEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM categories")
    suspend fun nextCategoryPosition(): Int

    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    /** Creates a category at the end of the list, or returns the existing one with that name. */
    @Transaction
    suspend fun createCategory(name: String): Long {
        val trimmed = name.trim()
        getCategories().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it.id }
        return insertCategory(CategoryEntity(name = trimmed, position = nextCategoryPosition()))
    }

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    /** Deletes a category; its recipes stay in the library. */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun setCategoryPosition(id: Long, position: Int)

    @Transaction
    suspend fun reorderCategories(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setCategoryPosition(id, index) }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addToCategory(link: RecipeCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategoryLink(link: RecipeCategoryEntity)

    @Query("SELECT * FROM recipe_categories WHERE recipeId = :recipeId")
    suspend fun getCategoryLinks(recipeId: Long): List<RecipeCategoryEntity>

    @Query("SELECT * FROM recipe_categories WHERE categoryId = :categoryId")
    suspend fun getCategoryMembers(categoryId: Long): List<RecipeCategoryEntity>

    @Query("DELETE FROM recipe_categories WHERE recipeId = :recipeId AND categoryId = :categoryId")
    suspend fun deleteCategoryLink(recipeId: Long, categoryId: Long)

    /**
     * Puts a recipe in exactly the chosen categories, as her choice. Categories
     * she unchecks keep a hidden link, so feeder tags don't add the recipe back.
     */
    @Transaction
    suspend fun setRecipeCategories(recipeId: Long, categoryIds: Collection<Long>) {
        val links = getCategoryLinks(recipeId).associateBy { it.categoryId }
        for (id in categoryIds) {
            val link = links[id]
            if (link == null || link.hidden) upsertCategoryLink(RecipeCategoryEntity(recipeId, id, TagSource.MANUAL))
        }
        for (link in links.values) {
            if (link.categoryId !in categoryIds && !link.hidden) upsertCategoryLink(link.copy(hidden = true))
        }
    }

    /** Adds a recipe to a category as her choice, such as from a category's suggestions. */
    @Transaction
    suspend fun addToCategoryByHand(recipeId: Long, categoryId: Long) {
        upsertCategoryLink(RecipeCategoryEntity(recipeId, categoryId, TagSource.MANUAL))
    }

    @Query("SELECT categoryId FROM recipe_categories WHERE recipeId = :recipeId AND hidden = 0")
    suspend fun getCategoryIds(recipeId: Long): List<Long>

    @Query(
        """
        SELECT c.* FROM categories c JOIN recipe_categories rc ON rc.categoryId = c.id
        WHERE rc.recipeId = :recipeId AND rc.hidden = 0 ORDER BY c.position, c.name COLLATE NOCASE
        """
    )
    fun observeRecipeCategories(recipeId: Long): Flow<List<CategoryEntity>>

    /** Every visible category membership, for counts on the home screen. */
    @Query("SELECT * FROM recipe_categories WHERE hidden = 0")
    fun observeCategoryLinks(): Flow<List<RecipeCategoryEntity>>

    @Query("UPDATE categories SET feederTags = :feederTags WHERE id = :id")
    suspend fun setFeederTags(id: Long, feederTags: List<String>)

    /** Recipes with any of [lowerNames] as a visible, confirmed tag. */
    @Query(
        """
        SELECT DISTINCT rt.recipeId FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
        WHERE rt.hidden = 0 AND rt.source != 'SUGGESTED' AND LOWER(t.name) IN (:lowerNames)
        """
    )
    suspend fun recipeIdsWithTags(lowerNames: List<String>): List<Long>

    // Saved pages

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePage(page: RecipePageEntity)

    @Query("SELECT * FROM recipe_pages WHERE recipeId = :recipeId")
    suspend fun getPage(recipeId: Long): RecipePageEntity?
}
