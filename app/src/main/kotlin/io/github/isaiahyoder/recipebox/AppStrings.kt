package io.github.isaiahyoder.recipebox

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * The app's text resources, for code that has no Context, such as card
 * readers and the backup manager. Screens use stringResource instead. Every
 * text she sees comes from res/values, so the app can be translated.
 */
interface AppStrings {
    fun get(@StringRes id: Int, vararg args: Any): String

    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String
}

/** [AppStrings] from the app's resources, in the phone's current language. */
class ResourceStrings(private val context: Context) : AppStrings {
    override fun get(id: Int, vararg args: Any): String = context.getString(id, *args)

    override fun plural(id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *args)
}
