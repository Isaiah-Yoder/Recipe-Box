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
 * categories and store sections as changed now.
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
        db.execSQL("ALTER TABLE `section_overrides` ADD COLUMN `changedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `section_overrides` SET `changedAt` = $NOW_MS")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deletions` (`uid` TEXT NOT NULL, `kind` TEXT NOT NULL, " +
                "`deletedAt` INTEGER NOT NULL, PRIMARY KEY(`uid`))"
        )
    }
}

/** Every hand-written migration, for the app's database and its tests. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_6_7)
