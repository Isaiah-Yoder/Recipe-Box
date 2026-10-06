package io.github.isaiahyoder.recipebox.data

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

@Entity(tableName = "grocery_lists")
data class GroceryListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Hides pantry staples such as salt and pepper. */
    val hideStaples: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** The units amounts are added up in, chosen per list. Lists from before 0.5.0 use US units. */
    @ColumnInfo(defaultValue = "US") val units: UnitSystem = UnitSystem.US,
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

    @Query("UPDATE grocery_lists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun renameList(id: Long, name: String, now: Long)

    @Query("UPDATE grocery_lists SET hideStaples = :hide WHERE id = :id")
    suspend fun setHideStaples(id: Long, hide: Boolean)

    @Query("UPDATE grocery_lists SET units = :units WHERE id = :id")
    suspend fun setUnits(id: Long, units: UnitSystem)

    @Query("UPDATE grocery_lists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM grocery_lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query(
        """
        SELECT r.id AS recipeId, r.title, r.ingredients, g.scale FROM grocery_list_recipes g
        JOIN recipes r ON r.id = g.recipeId WHERE g.listId = :listId ORDER BY g.addedAt
        """
    )
    fun observeRecipes(listId: Long): Flow<List<GroceryRecipeRow>>

    @Upsert
    suspend fun upsertRecipe(link: GroceryListRecipeEntity)

    @Query("SELECT * FROM grocery_list_recipes WHERE listId = :listId AND recipeId = :recipeId")
    suspend fun getRecipeLink(listId: Long, recipeId: Long): GroceryListRecipeEntity?

    @Query("UPDATE grocery_list_recipes SET scale = :scale WHERE listId = :listId AND recipeId = :recipeId")
    suspend fun setRecipeScale(listId: Long, recipeId: Long, scale: Double)

    @Query("DELETE FROM grocery_list_recipes WHERE listId = :listId AND recipeId = :recipeId")
    suspend fun removeRecipe(listId: Long, recipeId: Long)

    @Query("SELECT * FROM grocery_manual_items WHERE listId = :listId ORDER BY createdAt")
    fun observeManualItems(listId: Long): Flow<List<GroceryManualItemEntity>>

    @Insert
    suspend fun insertManualItem(item: GroceryManualItemEntity): Long

    @Query("UPDATE grocery_manual_items SET text = :text, section = :section WHERE id = :id")
    suspend fun updateManualItem(id: Long, text: String, section: String?)

    @Query("UPDATE grocery_manual_items SET checked = :checked WHERE id = :id")
    suspend fun setManualChecked(id: Long, checked: Boolean)

    @Query("DELETE FROM grocery_manual_items WHERE id = :id")
    suspend fun deleteManualItem(id: Long)

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
    }

    @Query("UPDATE grocery_line_state SET checked = 0 WHERE listId = :listId")
    suspend fun uncheckAllLines(listId: Long)

    @Query("UPDATE grocery_manual_items SET checked = 0 WHERE listId = :listId")
    suspend fun uncheckAllManual(listId: Long)

    @Query("SELECT * FROM section_overrides")
    fun observeSectionOverrides(): Flow<List<SectionOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSectionOverride(override: SectionOverrideEntity)
}
