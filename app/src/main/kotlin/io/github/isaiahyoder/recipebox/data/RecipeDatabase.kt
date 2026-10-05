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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // Version 2 adds the import queue table; recipes are unchanged.
        AutoMigration(from = 1, to = 2),
    ],
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    abstract fun importJobDao(): ImportJobDao

    companion object {
        fun create(context: Context): RecipeDatabase =
            Room.databaseBuilder(context, RecipeDatabase::class.java, "recipes.db")
                .build()
    }
}
