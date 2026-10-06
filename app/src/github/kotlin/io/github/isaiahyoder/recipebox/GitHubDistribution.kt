package io.github.isaiahyoder.recipebox

import android.content.Context
import androidx.compose.runtime.Composable
import io.github.isaiahyoder.recipebox.ui.update.UpdateSettingsItem
import io.github.isaiahyoder.recipebox.update.AppUpdater

/** Updates from the app's GitHub releases, installed by the app itself. */
class GitHubDistribution(val updater: AppUpdater) : AppDistribution {
    override fun onAppStart() = updater.checkIfDue()

    @Composable
    override fun UpdateBanner() = io.github.isaiahyoder.recipebox.ui.update.UpdateBanner()

    @Composable
    override fun SettingsItem() = UpdateSettingsItem()
}

/** The GitHub updater, for the update screens and Android's install answers. */
val Context.updater: AppUpdater
    get() = (appContainer.distribution as GitHubDistribution).updater
