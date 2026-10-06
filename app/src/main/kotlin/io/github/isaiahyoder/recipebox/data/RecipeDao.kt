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
    val cardPhotos: List<String>,
    val totalMinutes: Int?,
    val favorite: Boolean,
    val tagNames: String?,
)

/** A recipe and the name of one of its tags or suggestions. */
data class RecipeTagName(val recipeId: Long, val tag: String)

/** One recipe's card photo file names. */
data class CardPhotoNames(val cardPhotos: List<String>)

/** Escapes LIKE's wildcards so a search for "50%" finds "50%" and not every recipe with "50". */
fun escapeLike(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

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
            OR r.title LIKE '%' || :query || '%' ESCAPE '\'
            OR r.ingredientText LIKE '%' || :query || '%' ESCAPE '\'
            OR EXISTS (SELECT 1 FROM recipe_tags rt JOIN tags t ON t.id = rt.tagId
                WHERE rt.recipeId = r.id AND rt.hidden = 0 AND rt.source != 'SUGGESTED'
                AND t.name LIKE '%' || :query || '%' ESCAPE '\'))
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
    fun observeSummariesMatching(
        query: String,
        categoryId: Long,
        tag: String,
        favoritesOnly: Boolean,
    ): Flow<List<RecipeSummary>>

    /**
     * Recipes whose title, ingredients, or tags contain [query], in a category
     * or with a tag. "%" and "_" in the query match themselves.
     */
    fun observeSummaries(
        query: String,
        categoryId: Long = 0,
        tag: String = "",
        favoritesOnly: Boolean = false,
    ): Flow<List<RecipeSummary>> = observeSummariesMatching(escapeLike(query.trim()), categoryId, tag, favoritesOnly)

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observeRecipe(id: Long): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun getRecipe(id: Long): RecipeEntity?

    @Query("SELECT id FROM recipes WHERE sourceUrl = :url LIMIT 1")
    suspend fun findIdBySourceUrl(url: String): Long?

    @Insert
    suspend fun insertRow(recipe: RecipeEntity): Long

    @Update
    suspend fun updateRow(recipe: RecipeEntity)

    suspend fun insert(recipe: RecipeEntity): Long = insertRow(recipe.indexed())

    suspend fun update(recipe: RecipeEntity) = updateRow(recipe.indexed())

    /** Recipes whose search text is missing, such as those saved before version 6. */
    @Query("SELECT * FROM recipes WHERE ingredientText = '' AND ingredients != '[]'")
    suspend fun getUnindexedRecipes(): List<RecipeEntity>

    @Query("UPDATE recipes SET ingredientText = :text WHERE id = :id")
    suspend fun setIngredientText(id: Long, text: String)

    /** Fills the search text for recipes that don't have it yet. Returns how many. */
    @Transaction
    suspend fun indexRecipes(): Int {
        val missing = getUnindexedRecipes()
        missing.forEach { setIngredientText(it.id, it.indexed().ingredientText) }
        return missing.size
    }

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

    // Light queries, so work over the whole library never loads every recipe at once.

    /** Each recipe's photo and favorite flag, for the home screen's counts and photos. */
    @Query("SELECT id, favorite, imageFile, cardPhotos FROM recipes ORDER BY favorite DESC, updatedAt DESC")
    fun observeShelfRecipes(): Flow<List<ShelfRecipe>>

    /** Library rows for just these recipes, such as a category's suggestions. */
    @Query(
        """
        SELECT r.id, r.title, r.siteName, r.imageFile, r.cardPhotos, r.totalMinutes, r.favorite,
            (SELECT GROUP_CONCAT(t.name, '|') FROM recipe_tags rt
                JOIN tags t ON t.id = rt.tagId
                WHERE rt.recipeId = r.id AND rt.hidden = 0 AND rt.source != 'SUGGESTED') AS tagNames
        FROM recipes r WHERE r.id IN (:ids)
        ORDER BY r.favorite DESC, r.updatedAt DESC
        """
    )
    fun observeSummariesIn(ids: List<Long>): Flow<List<RecipeSummary>>

    @Query("SELECT id FROM recipes ORDER BY id")
    suspend fun allIds(): List<Long>

    /** Recipes with a web page to read again. */
    @Query("SELECT id FROM recipes WHERE sourceUrl IS NOT NULL ORDER BY id")
    suspend fun linkedIds(): List<Long>

    @Query("SELECT COUNT(*) FROM recipes WHERE sourceUrl IS NOT NULL")
    suspend fun countLinked(): Int

    @Query("SELECT COUNT(*) FROM recipes WHERE sourceUrl IS NOT NULL AND editedFields != '[]'")
    suspend fun countLinkedEdited(): Int
}

/** A recipe's photo and favorite flag, without its text. */
data class ShelfRecipe(val id: Long, val favorite: Boolean, val imageFile: String?, val cardPhotos: List<String>)
