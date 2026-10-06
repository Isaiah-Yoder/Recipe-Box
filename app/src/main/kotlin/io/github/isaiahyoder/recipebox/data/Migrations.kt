package io.github.isaiahyoder.recipebox.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** A random version 4 UUID, computed by SQLite for each row. */
private const val UUID_SQL = "lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || " +
    "substr(lower(hex(randomblob(2))), 2) || '-' || substr('89ab', 1 + (random() & 3), 1) || " +
    "substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6)))"

/**
 * Version 7 gives recipes, categories, and grocery lists a permanent uid and
 * a change time, and adds the table of deletions; see Identity.kt. Existing
 * recipes and lists count as changed when they were last updated, and
 * categories and store sections as changed now. It also records where each
 * recipe came from and adds the ingredient index, which the app fills for
 * existing recipes at startup.
 *
 * It's written by hand because an automatic migration can't give each row its
 * own uid before the unique index on uid is created.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for ((table, changedAt) in listOf("recipes" to "updatedAt", "categories" to NOW_MS, "grocery_lists" to "updatedAt")) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `uid` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `changedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `$table` SET `uid` = $UUID_SQL, `changedAt` = $changedAt")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_${table}_uid` ON `$table` (`uid`)")
        }
        // Where each recipe came from, judged from what it has: card photos, a link, or neither.
        db.execSQL("ALTER TABLE `recipes` ADD COLUMN `sourceKind` TEXT NOT NULL DEFAULT 'typed'")
        db.execSQL(
            "UPDATE `recipes` SET `sourceKind` = CASE WHEN `cardPhotos` != '[]' THEN 'card' " +
                "WHEN `sourceUrl` IS NOT NULL THEN 'web' ELSE 'typed' END"
        )
        db.execSQL("ALTER TABLE `section_overrides` ADD COLUMN `changedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `section_overrides` SET `changedAt` = $NOW_MS")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deletions` (`uid` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recipe_ingredients` (`recipeId` INTEGER NOT NULL, `position` INTEGER NOT NULL, " +
                "`groupName` TEXT, `nameKey` TEXT NOT NULL, `name` TEXT NOT NULL, `amountLow` REAL, `amountHigh` REAL, " +
                "`unit` TEXT, `measure` TEXT, `baseAmount` REAL, `size` TEXT, PRIMARY KEY(`recipeId`, `position`), " +
                "FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recipe_ingredients_nameKey` ON `recipe_ingredients` (`nameKey`)")
    }
}

/** Every hand-written migration, for the app's database and its tests. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_6_7)
