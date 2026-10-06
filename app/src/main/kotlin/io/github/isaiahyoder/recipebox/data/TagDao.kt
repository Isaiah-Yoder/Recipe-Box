package io.github.isaiahyoder.recipebox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.github.isaiahyoder.recipebox.tags.TagResult
import kotlinx.coroutines.flow.Flow

/** Tags on recipes: automatic, hers, suggested, and hidden ones she removed. */
@Dao
interface TagDao {
    /** Tag names on at least one recipe, for filters and suggestions. */
    @Query(
        """
        SELECT DISTINCT t.name FROM tags t JOIN recipe_tags rt ON rt.tagId = t.id
        WHERE rt.hidden = 0 AND rt.source != 'SUGGESTED' ORDER BY t.name COLLATE NOCASE
        """
    )
    fun observeTagNamesInUse(): Flow<List<String>>

    @Query(
        """
        SELECT t.name FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
        WHERE rt.recipeId = :recipeId AND rt.hidden = 0 AND rt.source != 'SUGGESTED' ORDER BY t.name
        """
    )
    fun observeTagNames(recipeId: Long): Flow<List<String>>

    /** A recipe's visible, confirmed tags, the ones that fill categories. */
    @Query(
        """
        SELECT t.name FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
        WHERE rt.recipeId = :recipeId AND rt.hidden = 0 AND rt.source != 'SUGGESTED'
        """
    )
    suspend fun getTagNames(recipeId: Long): List<String>

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

    /** Her tag choices are part of the recipe, so they count as a change to it. Automatic tags don't. */
    @Query("UPDATE recipes SET changedAt = $NOW_MS WHERE id = :recipeId")
    suspend fun markRecipeChanged(recipeId: Long)

    /** Adds a tag she chose. It becomes hers, so recomputing automatic tags never removes it. */
    @Transaction
    suspend fun addManualTag(recipeId: Long, name: String) {
        val tagId = findTagId(name) ?: insertTag(TagEntity(name = name))
        upsertRecipeTag(RecipeTagEntity(recipeId, tagId, TagSource.MANUAL))
        markRecipeChanged(recipeId)
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
        markRecipeChanged(recipeId)
    }


    /** Deletes tag names that no recipe uses any more, including hidden links. */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM recipe_tags)")
    suspend fun deleteUnusedTags()

    /** Recipes with any of [lowerNames] as a visible, confirmed tag. */
    @Query(
        """
        SELECT DISTINCT rt.recipeId FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
        WHERE rt.hidden = 0 AND rt.source != 'SUGGESTED' AND LOWER(t.name) IN (:lowerNames)
        """
    )
    suspend fun recipeIdsWithTags(lowerNames: List<String>): List<Long>
}
