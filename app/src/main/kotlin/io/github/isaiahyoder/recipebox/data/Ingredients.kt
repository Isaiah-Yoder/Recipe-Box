package io.github.isaiahyoder.recipebox.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Query
import io.github.isaiahyoder.recipebox.ingredients.IndexedIngredient
import io.github.isaiahyoder.recipebox.model.RecipeLine
import kotlinx.coroutines.flow.Flow

/**
 * One ingredient line of a recipe, read into its name, amount, and unit, so
 * features can query ingredients across the library: which recipes use
 * chicken, what a week's meals need, or what she can make from her pantry.
 *
 * It's derived from the recipe's ingredient lines, the way search text is:
 * RecipeDao rewrites a recipe's rows whenever it saves the recipe, so it's
 * never edited directly and isn't in backups.
 */
@Entity(
    tableName = "recipe_ingredients",
    primaryKeys = ["recipeId", "position"],
    foreignKeys = [ForeignKey(RecipeEntity::class, ["id"], ["recipeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("nameKey")],
)
data class RecipeIngredientEntity(
    val recipeId: Long,
    /** The line's place in the recipe's ingredient list, headings included. */
    val position: Int,
    /** The heading the line is under, such as "For the sauce", or null. */
    val groupName: String?,
    /** Matches the same ingredient across recipes, such as "green onion" for "3 scallions". */
    val nameKey: String,
    /** The name as written, without amounts or preparation. */
    val name: String,
    val amountLow: Double?,
    val amountHigh: Double?,
    /** An [io.github.isaiahyoder.recipebox.ingredients.Unit] name, or null for a plain count or no amount. */
    val unit: String?,
    /** The unit's [io.github.isaiahyoder.recipebox.ingredients.Measure] name, or null. */
    val measure: String?,
    /** The larger amount in its measure's base unit, such as teaspoons, for adding up; null for counts. */
    val baseAmount: Double?,
    /** A container or plain size, such as "15 ounce" or "9-inch". */
    val size: String?,
) {
    companion object {
        fun from(recipeId: Long, ingredient: IndexedIngredient): RecipeIngredientEntity {
            val reading = ingredient.reading
            return RecipeIngredientEntity(
                recipeId = recipeId,
                position = ingredient.position,
                groupName = ingredient.group,
                nameKey = reading.name.key,
                name = reading.name.display,
                amountLow = reading.quantity?.low,
                amountHigh = reading.quantity?.high,
                unit = reading.unit?.name,
                measure = reading.unit?.measure?.name,
                baseAmount = reading.baseAmount,
                size = reading.size ?: reading.plainSize,
            )
        }
    }
}

/** A recipe's ingredient lines, for building its index rows. */
data class RecipeIngredientLines(val id: Long, val ingredients: List<RecipeLine>)

/** An ingredient and how many recipes use it. */
data class IngredientUse(val nameKey: String, val name: String, val recipeCount: Int)

/** Questions about ingredients across the library. RecipeDao keeps the rows current. */
@Dao
interface IngredientDao {
    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY position")
    suspend fun getIngredients(recipeId: Long): List<RecipeIngredientEntity>

    /** Recipes that use any of the ingredients, by [RecipeIngredientEntity.nameKey]. */
    @Query("SELECT DISTINCT recipeId FROM recipe_ingredients WHERE nameKey IN (:nameKeys) ORDER BY recipeId")
    suspend fun recipeIdsUsing(nameKeys: Collection<String>): List<Long>

    /** Every ingredient in the library, most used first. */
    @Query(
        """
        SELECT nameKey, MIN(name) AS name, COUNT(DISTINCT recipeId) AS recipeCount FROM recipe_ingredients
        GROUP BY nameKey ORDER BY recipeCount DESC, nameKey
        """
    )
    fun observeIngredientUses(): Flow<List<IngredientUse>>
}
