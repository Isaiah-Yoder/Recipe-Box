package io.github.isaiahyoder.recipebox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Reads and replaces everything a backup holds. Import queue entries aren't backed up. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM recipes") suspend fun recipes(): List<RecipeEntity>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM recipe_tags") suspend fun recipeTags(): List<RecipeTagEntity>
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM recipe_categories") suspend fun recipeCategories(): List<RecipeCategoryEntity>
    @Query("SELECT * FROM grocery_lists") suspend fun groceryLists(): List<GroceryListEntity>
    @Query("SELECT * FROM grocery_list_recipes") suspend fun groceryListRecipes(): List<GroceryListRecipeEntity>
    @Query("SELECT * FROM grocery_manual_items") suspend fun groceryManualItems(): List<GroceryManualItemEntity>
    @Query("SELECT * FROM grocery_line_state") suspend fun groceryLineStates(): List<GroceryLineStateEntity>
    @Query("SELECT * FROM section_overrides") suspend fun sectionOverrides(): List<SectionOverrideEntity>
    @Query("SELECT * FROM deletions") suspend fun deletions(): List<DeletionEntity>

    @Query("DELETE FROM grocery_line_state") suspend fun clearGroceryLineStates()
    @Query("DELETE FROM grocery_manual_items") suspend fun clearGroceryManualItems()
    @Query("DELETE FROM grocery_list_recipes") suspend fun clearGroceryListRecipes()
    @Query("DELETE FROM grocery_lists") suspend fun clearGroceryLists()
    @Query("DELETE FROM section_overrides") suspend fun clearSectionOverrides()
    @Query("DELETE FROM recipe_categories") suspend fun clearRecipeCategories()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM recipe_tags") suspend fun clearRecipeTags()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM recipes") suspend fun clearRecipes()
    @Query("DELETE FROM deletions") suspend fun clearDeletions()

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRecipes(items: List<RecipeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTags(items: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRecipeTags(items: List<RecipeTagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(items: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertRecipeCategories(items: List<RecipeCategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGroceryLists(items: List<GroceryListEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGroceryListRecipes(items: List<GroceryListRecipeEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGroceryManualItems(items: List<GroceryManualItemEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGroceryLineStates(items: List<GroceryLineStateEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertSectionOverrides(items: List<SectionOverrideEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertDeletions(items: List<DeletionEntity>)
}
