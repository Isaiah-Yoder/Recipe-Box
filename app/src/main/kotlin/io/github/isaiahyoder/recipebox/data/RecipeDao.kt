package io.github.isaiahyoder.recipebox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A recipe row for the library list, without the ingredient and step text. */
data class RecipeSummary(
    val id: Long,
    val title: String,
    val siteName: String?,
    val imageFile: String?,
    val totalMinutes: Int?,
    val favorite: Boolean,
    val tagNames: String?,
)

@Dao
interface RecipeDao {
    @Query(
        """
        SELECT r.id, r.title, r.siteName, r.imageFile, r.totalMinutes, r.favorite,
            (SELECT GROUP_CONCAT(t.name, '|') FROM recipe_tags rt
                JOIN tags t ON t.id = rt.tagId
                WHERE rt.recipeId = r.id AND rt.hidden = 0) AS tagNames
        FROM recipes r
        WHERE (:query = ''
            OR r.title LIKE '%' || :query || '%'
            OR r.ingredients LIKE '%' || :query || '%'
            OR EXISTS (SELECT 1 FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
                WHERE rt.recipeId = r.id AND rt.hidden = 0 AND t.name LIKE '%' || :query || '%'))
          AND (:categoryId = 0 OR EXISTS (SELECT 1 FROM recipe_categories rc
                WHERE rc.recipeId = r.id AND rc.categoryId = :categoryId))
          AND (:tag = '' OR EXISTS (SELECT 1 FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
                WHERE rt.recipeId = r.id AND rt.hidden = 0 AND t.name = :tag))
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
        WHERE rt.hidden = 0 ORDER BY t.name COLLATE NOCASE
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
        WHERE rt.recipeId = :recipeId AND rt.hidden = 0 ORDER BY t.name
        """
    )
    fun observeTagNames(recipeId: Long): Flow<List<String>>

    @Query("SELECT id FROM tags WHERE name = :name")
    suspend fun findTagId(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT * FROM recipe_tags WHERE recipeId = :recipeId")
    suspend fun getRecipeTags(recipeId: Long): List<RecipeTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecipeTag(link: RecipeTagEntity)

    @Query("DELETE FROM recipe_tags WHERE recipeId = :recipeId AND source = 'AUTO' AND hidden = 0")
    suspend fun clearVisibleAutoTags(recipeId: Long)

    /**
     * Replaces a recipe's automatic tags. Tags the user hid stay hidden, and
     * tags the user added by hand are left alone.
     */
    @Transaction
    suspend fun replaceAutoTags(recipeId: Long, names: Collection<String>) {
        val existing = getRecipeTags(recipeId).associateBy { it.tagId }
        clearVisibleAutoTags(recipeId)
        for (name in names) {
            val tagId = findTagId(name) ?: insertTag(TagEntity(name = name))
            val link = existing[tagId]
            if (link == null || (link.source == TagSource.AUTO && !link.hidden)) {
                upsertRecipeTag(RecipeTagEntity(recipeId, tagId, TagSource.AUTO))
            }
        }
    }

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

    @Query("DELETE FROM recipe_categories WHERE recipeId = :recipeId")
    suspend fun clearRecipeCategories(recipeId: Long)

    @Transaction
    suspend fun setRecipeCategories(recipeId: Long, categoryIds: Collection<Long>) {
        clearRecipeCategories(recipeId)
        categoryIds.forEach { addToCategory(RecipeCategoryEntity(recipeId, it)) }
    }

    @Query("SELECT categoryId FROM recipe_categories WHERE recipeId = :recipeId")
    suspend fun getCategoryIds(recipeId: Long): List<Long>

    @Query(
        """
        SELECT c.* FROM categories c JOIN recipe_categories rc ON rc.categoryId = c.id
        WHERE rc.recipeId = :recipeId ORDER BY c.position, c.name COLLATE NOCASE
        """
    )
    fun observeRecipeCategories(recipeId: Long): Flow<List<CategoryEntity>>
}
