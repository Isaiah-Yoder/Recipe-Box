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
        RecipePageEntity::class,
        DeletionEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        // Version 2 adds the import queue table; recipes are unchanged.
        AutoMigration(from = 1, to = 2),
        // Version 3 adds grocery list tables; existing tables are unchanged.
        AutoMigration(from = 2, to = 3),
        // Version 4 adds recipe card photos to recipes, defaulting to none.
        AutoMigration(from = 3, to = 4),
        // Version 5 adds edited parts to recipes, feeder tags to categories, where each
        // category membership came from, and saved recipe pages. Existing memberships
        // become hers (MANUAL), so nothing she sorted changes.
        AutoMigration(from = 4, to = 5),
        // Version 6 adds the ingredient lines as plain text for search. The app fills
        // it for existing recipes at startup.
        AutoMigration(from = 5, to = 6),
        // Version 7 is MIGRATION_6_7, written by hand.
    ],
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    abstract fun tagDao(): TagDao

    abstract fun categoryDao(): CategoryDao

    abstract fun pageDao(): PageDao

    abstract fun importJobDao(): ImportJobDao

    abstract fun groceryDao(): GroceryDao

    abstract fun backupDao(): BackupDao

    companion object {
        fun create(context: Context): RecipeDatabase =
            Room.databaseBuilder(context, RecipeDatabase::class.java, "recipes.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
