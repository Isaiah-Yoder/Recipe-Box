package io.github.isaiahyoder.recipebox.repository

import androidx.room.withTransaction
import io.github.isaiahyoder.recipebox.data.GroceryListEntity
import io.github.isaiahyoder.recipebox.data.GroceryListRecipeEntity
import io.github.isaiahyoder.recipebox.data.GroceryListSummary
import io.github.isaiahyoder.recipebox.data.RecipeDatabase
import io.github.isaiahyoder.recipebox.settings.AppSettings
import kotlinx.coroutines.flow.Flow

/** Creating grocery lists and adding recipes to them. */
class GroceryRepository(
    private val database: RecipeDatabase,
    private val settings: AppSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = database.groceryDao()

    fun observeLists(): Flow<List<GroceryListSummary>> = dao.observeSummaries()

    /** Creates a list in her default units and returns its id. */
    suspend fun createList(name: String): Long {
        val now = clock()
        return dao.insertList(GroceryListEntity(name = name.trim(), createdAt = now, updatedAt = now, units = settings.unitSystem.value))
    }

    /**
     * Adds a recipe at [scale] to the list [listId], or to a new list named
     * [newListName] when it's given. A recipe already on the list gets the
     * new scale. Returns the list's id.
     */
    suspend fun addRecipe(recipeId: Long, scale: Double, listId: Long, newListName: String? = null): Long =
        database.withTransaction {
            val id = if (newListName != null) createList(newListName) else listId
            val now = clock()
            dao.upsertRecipe(GroceryListRecipeEntity(id, recipeId, scale, now))
            dao.touch(id, now)
            id
        }
}
