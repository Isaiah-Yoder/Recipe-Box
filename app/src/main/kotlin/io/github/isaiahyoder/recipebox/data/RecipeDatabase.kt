package io.github.isaiahyoder.recipebox.data

import android.content.Context
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
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class RecipeDatabase : RoomDatabase() {
    abstract fun recipeDao(): RecipeDao

    companion object {
        fun create(context: Context): RecipeDatabase =
            Room.databaseBuilder(context, RecipeDatabase::class.java, "recipes.db")
                .build()
    }
}
