package io.github.isaiahyoder.recipebox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** The saved recipe parts of web pages, for reading recipes again offline. */
@Dao
interface PageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePage(page: RecipePageEntity)

    @Query("SELECT * FROM recipe_pages WHERE recipeId = :recipeId")
    suspend fun getPage(recipeId: Long): RecipePageEntity?

    /** Recipes with a saved page, without loading the pages. */
    @Query("SELECT recipeId FROM recipe_pages ORDER BY recipeId")
    suspend fun recipeIdsWithPage(): List<Long>

    /** Recipes with a link but no saved page, such as those imported before pages were saved. */
    @Query(
        """
        SELECT r.id FROM recipes r WHERE r.sourceUrl IS NOT NULL
            AND NOT EXISTS (SELECT 1 FROM recipe_pages p WHERE p.recipeId = r.id)
        ORDER BY r.id
        """
    )
    suspend fun linkedIdsWithoutPage(): List<Long>
}
