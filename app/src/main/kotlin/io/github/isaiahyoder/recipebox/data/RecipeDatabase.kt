package io.github.isaiahyoder.recipebox.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app's only database.
 *
 * Every schema change must raise [version] and add a migration. Never use a
 * destructive fallback: it would delete her recipes on update.
 */
@Database(
    entities = [
        RecipeEntity::class,
        TagEntity::class,
        RecipeTagEntity::class,
        CategoryEntity::class,
        RecipeCategoryEntity::class,
        ImportJobEntity::class,
        GroceryListEntity::class,
        GroceryListRecipeEntity::class,
        GroceryManualItemEntity::class,
        GroceryLineStateEntity::class,
        SectionOverrideEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // Version 2 adds the import queue table; recipes are unchanged.
        AutoMigration(from = 1, to = 2),
        // Version 3 adds grocery list tables; existing tables are unchanged.
        AutoMigration(from = 2, to = 3),
        // Version 4 adds recipe card photos to recipes, defaulting to none.
        AutoMigration(from = 3, to = 4),
    ],
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    abstract fun importJobDao(): ImportJobDao

    abstract fun groceryDao(): GroceryDao

    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): RecipeDatabase =
            Room.databaseBuilder(context, RecipeDatabase::class.java, "recipes.db")
                .build()
    }
}
