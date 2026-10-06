package io.github.isaiahyoder.recipebox

import androidx.compose.runtime.Composable
import io.github.isaiahyoder.recipebox.ui.update.UpdateSettingsItem
import io.github.isaiahyoder.recipebox.update.AppUpdater

/**
 * How this build of the app gets updates. Shared code calls only this, so a
 * build for an app store can supply its own: one that lets the store update
 * the app, without the GitHub updater or its install permission.
 */
interface AppDistribution {
    /** Checks for an update when one is due, as the app comes to the front. */
    fun onAppStart()

    /** Offers an update at the bottom of the home screen, or shows nothing. */
    @Composable
    fun UpdateBanner()

    /** The update item in Settings, or nothing. */
    @Composable
    fun SettingsItem()
}

/** Updates from the app's GitHub releases, installed by the app itself. */
class GitHubDistribution(private val updater: AppUpdater) : AppDistribution {
    override fun onAppStart() = updater.checkIfDue()

    @Composable
    override fun UpdateBanner() = io.github.isaiahyoder.recipebox.ui.update.UpdateBanner()

    @Composable
    override fun SettingsItem() = UpdateSettingsItem()
}
