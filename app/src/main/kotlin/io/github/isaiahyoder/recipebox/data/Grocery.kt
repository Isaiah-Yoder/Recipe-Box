package io.github.isaiahyoder.recipebox.data

import io.github.isaiahyoder.recipebox.model.RecipeLine
import androidx.room.ColumnInfo
import io.github.isaiahyoder.recipebox.ingredients.UnitSystem
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "grocery_lists", indices = [Index(value = ["uid"], unique = true)])
data class GroceryListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Hides pantry staples such as salt and pepper. */
    val hideStaples: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** The units amounts are added up in, chosen per list. Lists from before 0.5.0 use US units. */
    @ColumnInfo(defaultValue = "US") val units: UnitSystem = UnitSystem.US,
    /** Permanent identity; see Identity.kt. */
    @ColumnInfo(defaultValue = "") val uid: String = newUid(),
    /** When the list, its recipes, or its items last changed; see Identity.kt. */
    @ColumnInfo(defaultValue = "0") val changedAt: Long = System.currentTimeMillis(),
)

/** A recipe on a list, at its own scale. */
@Entity(
    tableName = "grocery_list_recipes",
    primaryKeys = ["listId", "recipeId"],
    foreignKeys = [
        ForeignKey(GroceryListEntity::class, ["id"], ["listId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("recipeId")],
)
data class GroceryListRecipeEntity(
    val listId: Long,
    val recipeId: Long,
    val scale: Double,
    val addedAt: Long,
)

/** An item she typed in herself. It stays when recipes are added or removed. */
@Entity(
    tableName = "grocery_manual_items",
    foreignKeys = [ForeignKey(GroceryListEntity::class, ["id"], ["listId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listId")],
)
data class GroceryManualItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val text: String,
    /** A [io.github.isaiahyoder.recipebox.grocery.StoreSection] name, or null to guess from the text. */
    val section: String? = null,
    val checked: Boolean = false,
    val createdAt: Long,
)

/**
 * What she did to a line built from recipes: checked it, deleted it, or
 * rewrote it. Keyed by the ingredient's name, so the state survives scale
 * changes and recipes being added.
 */
@Entity(
    tableName = "grocery_line_state",
    primaryKeys = ["listId", "lineKey"],
    foreignKeys = [ForeignKey(GroceryListEntity::class, ["id"], ["listId"], onDelete = ForeignKey.CASCADE)],
)
data class GroceryLineStateEntity(
    val listId: Long,
    val lineKey: String,
    val checked: Boolean = false,
    val hidden: Boolean = false,
    val customText: String? = null,
)

/** Her choice of store section for an ingredient, remembered for every list. */
@Entity(tableName = "section_overrides")
data class SectionOverrideEntity(
    @PrimaryKey val nameKey: String,
    val section: String,
    @ColumnInfo(defaultValue = "0") val changedAt: Long = System.currentTimeMillis(),
)

/** A recipe on a list, with what the list needs to build its lines. */
data class GroceryRecipeRow(
    val recipeId: Long,
    val title: String,
    val ingredients: List<RecipeLine>,
    val scale: Double,
)

data class GroceryListSummary(
    val id: Long,
    val name: String,
    val recipeCount: Int,
    val manualCount: Int,
)

@Dao
interface GroceryDao {
    @Query(
        """
        SELECT l.id, l.name,
            (SELECT COUNT(*) FROM grocery_list_recipes r WHERE r.listId = l.id) AS recipeCount,
            (SELECT COUNT(*) FROM grocery_manual_items m WHERE m.listId = l.id) AS manualCount
        FROM grocery_lists l ORDER BY l.updatedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<GroceryListSummary>>

    @Query("SELECT * FROM grocery_lists ORDER BY updatedAt DESC")
    suspend fun getLists(): List<GroceryListEntity>

    @Query("SELECT * FROM grocery_lists WHERE id = :id")
    fun observeList(id: Long): Flow<GroceryListEntity?>

    @Insert
    suspend fun insertList(list: GroceryListEntity): Long

    /** A change to a list, its recipes, or its items; see Identity.kt. */
    @Query("UPDATE grocery_lists SET changedAt = $NOW_MS WHERE id = :id")
    suspend fun markListChanged(id: Long)

    @Query("UPDATE grocery_lists SET changedAt = $NOW_MS WHERE id = (SELECT listId FROM grocery_manual_items WHERE id = :itemId)")
    suspend fun markItemListChanged(itemId: Long)

    @Query("UPDATE grocery_lists SET name = :name, updatedAt = :now, changedAt = :now WHERE id = :id")
    suspend fun renameList(id: Long, name: String, now: Long)

    @Query("UPDATE grocery_lists SET hideStaples = :hide, changedAt = $NOW_MS WHERE id = :id")
    suspend fun setHideStaples(id: Long, hide: Boolean)

    @Query("UPDATE grocery_lists SET units = :units, changedAt = $NOW_MS WHERE id = :id")
    suspend fun setUnits(id: Long, units: UnitSystem)

    /** Moves a list to the top of her lists, after something was added to it. */
    @Query("UPDATE grocery_lists SET updatedAt = :now, changedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM grocery_lists WHERE id = :id")
    suspend fun deleteListRow(id: Long)

    @Query("SELECT uid FROM grocery_lists WHERE id = :id")
    suspend fun getListUid(id: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordDeletion(deletion: DeletionEntity)

    /** Deletes a list and records the deletion for later merges. */
    @Transaction
    suspend fun deleteList(id: Long) {
        val uid = getListUid(id) ?: return
        recordDeletion(DeletionEntity(uid, DeletedKind.GROCERY_LIST, System.currentTimeMillis()))
        deleteListRow(id)
    }

    @Query(
        """
        SELECT r.id AS recipeId, r.title, r.ingredients, g.scale FROM grocery_list_recipes g
        JOIN recipes r ON r.id = g.recipeId WHERE g.listId = :listId ORDER BY g.addedAt
        """
    )
    fun observeRecipes(listId: Long): Flow<List<GroceryRecipeRow>>

    @Upsert
    suspend fun upsertRecipeRow(link: GroceryListRecipeEntity)

    @Transaction
    suspend fun upsertRecipe(link: GroceryListRecipeEntity) {
        upsertRecipeRow(link)
        markListChanged(link.listId)
    }

    @Query("SELECT * FROM grocery_list_recipes WHERE listId = :listId AND recipeId = :recipeId")
    suspend fun getRecipeLink(listId: Long, recipeId: Long): GroceryListRecipeEntity?

    @Query("UPDATE grocery_list_recipes SET scale = :scale WHERE listId = :listId AND recipeId = :recipeId")
    suspend fun setRecipeScaleRow(listId: Long, recipeId: Long, scale: Double)

    @Transaction
    suspend fun setRecipeScale(listId: Long, recipeId: Long, scale: Double) {
        setRecipeScaleRow(listId, recipeId, scale)
        markListChanged(listId)
    }

    @Query("DELETE FROM grocery_list_recipes WHERE listId = :listId AND recipeId = :recipeId")
    suspend fun removeRecipeRow(listId: Long, recipeId: Long)

    @Transaction
    suspend fun removeRecipe(listId: Long, recipeId: Long) {
        removeRecipeRow(listId, recipeId)
        markListChanged(listId)
    }

    @Query("SELECT * FROM grocery_manual_items WHERE listId = :listId ORDER BY createdAt")
    fun observeManualItems(listId: Long): Flow<List<GroceryManualItemEntity>>

    @Insert
    suspend fun insertManualItemRow(item: GroceryManualItemEntity): Long

    @Transaction
    suspend fun insertManualItem(item: GroceryManualItemEntity): Long =
        insertManualItemRow(item).also { markListChanged(item.listId) }

    @Query("UPDATE grocery_manual_items SET text = :text, section = :section WHERE id = :id")
    suspend fun updateManualItemRow(id: Long, text: String, section: String?)

    @Transaction
    suspend fun updateManualItem(id: Long, text: String, section: String?) {
        updateManualItemRow(id, text, section)
        markItemListChanged(id)
    }

    @Query("UPDATE grocery_manual_items SET checked = :checked WHERE id = :id")
    suspend fun setManualCheckedRow(id: Long, checked: Boolean)

    @Transaction
    suspend fun setManualChecked(id: Long, checked: Boolean) {
        setManualCheckedRow(id, checked)
        markItemListChanged(id)
    }

    @Query("DELETE FROM grocery_manual_items WHERE id = :id")
    suspend fun deleteManualItemRow(id: Long)

    @Transaction
    suspend fun deleteManualItem(id: Long) {
        markItemListChanged(id)
        deleteManualItemRow(id)
    }

    @Query("SELECT * FROM grocery_line_state WHERE listId = :listId")
    fun observeLineStates(listId: Long): Flow<List<GroceryLineStateEntity>>

    @Query("SELECT * FROM grocery_line_state WHERE listId = :listId AND lineKey = :lineKey")
    suspend fun getLineState(listId: Long, lineKey: String): GroceryLineStateEntity?

    @Upsert
    suspend fun upsertLineState(state: GroceryLineStateEntity)

    @Transaction
    suspend fun updateLineState(listId: Long, lineKey: String, change: (GroceryLineStateEntity) -> GroceryLineStateEntity) {
        val current = getLineState(listId, lineKey) ?: GroceryLineStateEntity(listId, lineKey)
        upsertLineState(change(current))
        markListChanged(listId)
    }

    @Query("UPDATE grocery_line_state SET checked = 0 WHERE listId = :listId")
    suspend fun uncheckAllLinesRows(listId: Long)

    @Query("UPDATE grocery_manual_items SET checked = 0 WHERE listId = :listId")
    suspend fun uncheckAllManualRows(listId: Long)

    /** Unchecks every line and item, such as before the next shopping trip. */
    @Transaction
    suspend fun uncheckAll(listId: Long) {
        uncheckAllLinesRows(listId)
        uncheckAllManualRows(listId)
        markListChanged(listId)
    }

    @Query("SELECT * FROM section_overrides")
    fun observeSectionOverrides(): Flow<List<SectionOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSectionOverride(override: SectionOverrideEntity)
}
