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
        WHERE :query = ''
            OR r.title LIKE '%' || :query || '%'
            OR r.ingredients LIKE '%' || :query || '%'
            OR EXISTS (SELECT 1 FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
                WHERE rt.recipeId = r.id AND rt.hidden = 0 AND t.name LIKE '%' || :query || '%')
        ORDER BY r.favorite DESC, r.updatedAt DESC
        """
    )
    fun observeSummaries(query: String): Flow<List<RecipeSummary>>

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
}
