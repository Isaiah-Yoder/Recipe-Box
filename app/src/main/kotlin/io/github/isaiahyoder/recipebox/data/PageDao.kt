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
}
