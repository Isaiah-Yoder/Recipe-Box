package io.github.isaiahyoder.recipebox.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Categories and which recipes are in them. */
@Dao
interface CategoryDao {
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

    @Query("UPDATE categories SET name = :name, changedAt = $NOW_MS WHERE id = :id")
    suspend fun setCategoryName(id: Long, name: String)

    /**
     * Renames a category unless another one already has the name, ignoring
     * case. Returns false when the name is taken; the name must stay unique.
     */
    @Transaction
    suspend fun renameCategory(id: Long, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        if (getCategories().any { it.id != id && it.name.equals(trimmed, ignoreCase = true) }) return false
        setCategoryName(id, trimmed)
        return true
    }

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategoryRow(id: Long)

    @Query("SELECT uid FROM categories WHERE id = :id")
    suspend fun getCategoryUid(id: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordDeletion(deletion: DeletionEntity)

    /** Deletes a category and records the deletion; its recipes stay in the library. */
    @Transaction
    suspend fun deleteCategory(id: Long) {
        val uid = getCategoryUid(id) ?: return
        recordDeletion(DeletionEntity(uid, DeletedKind.CATEGORY, System.currentTimeMillis()))
        deleteCategoryRow(id)
    }

    /** Her category choices are part of the recipe, so they count as a change to it. Feeder tags' don't. */
    @Query("UPDATE recipes SET changedAt = $NOW_MS WHERE id = :recipeId")
    suspend fun markRecipeChanged(recipeId: Long)

    @Query("UPDATE categories SET position = :position, changedAt = $NOW_MS WHERE id = :id AND position != :position")
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
        markRecipeChanged(recipeId)
    }

    /** Adds a recipe to a category as her choice, such as from a category's suggestions. */
    @Transaction
    suspend fun addToCategoryByHand(recipeId: Long, categoryId: Long) {
        upsertCategoryLink(RecipeCategoryEntity(recipeId, categoryId, TagSource.MANUAL))
        markRecipeChanged(recipeId)
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

    @Query("UPDATE categories SET feederTags = :feederTags, changedAt = $NOW_MS WHERE id = :id")
    suspend fun setFeederTags(id: Long, feederTags: List<String>)
}
