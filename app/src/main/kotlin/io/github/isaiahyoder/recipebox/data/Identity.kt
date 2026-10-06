package io.github.isaiahyoder.recipebox.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/*
 * Identity and change tracking, so recipes can later be merged between
 * phones, accounts, and backups.
 *
 * A recipe, category, or grocery list has a permanent uid, the same on every
 * phone and in every backup, unlike its row id, which only this phone's
 * database uses. Each also records changedAt: when she, or a refresh from
 * its website, last changed anything a backup keeps. A change to a part that
 * belongs to it, such as a recipe's tags or a list's items, counts as a
 * change to it. Work the phone redoes on its own, such as automatic tags,
 * category filling, search text, and downloaded photo files, doesn't count.
 * Deleting one of them leaves a DeletionEntity, so a merge can tell a
 * deleted recipe from one this phone never had.
 *
 * Tags need no uid: a tag is its name.
 */

/** A new permanent identity. */
fun newUid(): String = UUID.randomUUID().toString()

/** The current time in milliseconds since 1970, computed by SQLite inside a query. */
const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)"

/** Kinds of records a [DeletionEntity] can name. */
object DeletedKind {
    const val RECIPE = "recipe"
    const val CATEGORY = "category"
    const val GROCERY_LIST = "grocery_list"
}

/** A record she deleted, kept so merges don't bring it back. */
@Entity(tableName = "deletions")
data class DeletionEntity(
    @PrimaryKey val uid: String,
    /** A [DeletedKind] name. */
    val kind: String,
    val deletedAt: Long,
)
