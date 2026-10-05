package io.github.isaiahyoder.recipebox.update

import io.github.isaiahyoder.recipebox.util.runCatchingCancellable
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val checkedAt: Long) : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState

    /** [confirm] is Android's install screen, kept in case it couldn't open on its own. */
    data class Installing(val update: AvailableUpdate, val confirm: Intent? = null) : UpdateState
    data class Failed(val update: AvailableUpdate?, val message: String) : UpdateState
}

/**
 * Updates the app from its GitHub releases, so no other app is needed.
 *
 * It checks the latest release when the app opens, downloads the APK when
 * she taps Update, and hands it to Android's installer. Android only accepts
 * the APK when it's signed with the same key as the installed app and has a
 * higher version code, so recipes and settings carry over as with any update.
 */
class AppUpdater(
    private val context: Context,
    private val http: OkHttpClient,
    private val settings: AppSettings,
) {
    /** Test builds have their own app ID; installing a release from one would add a second app. */
    val enabled: Boolean = !context.packageName.endsWith(".debug")

    private val installed: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val installedVersion: String = installed.versionName ?: "0"

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checking = Mutex()
    private var lastCheck = 0L

    private val downloadFolder get() = File(context.cacheDir, "updates")

    /** Checks GitHub at most once an hour while the app stays open. */
    fun checkIfDue(now: Long = System.currentTimeMillis()) {
        if (!enabled || now - lastCheck < TimeUnit.HOURS.toMillis(1)) return
        lastCheck = now
        scope.launch {
            // An APK left from an earlier update is no longer needed.
            if (_state.value == UpdateState.Idle) downloadFolder.deleteRecursively()
            check()
        }
    }

    suspend fun check(): UpdateState = checking.withLock {
        if (!enabled) return@withLock _state.value
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Installing) return@withLock busy
        _state.value = UpdateState.Checking
        val result = runCatchingCancellable { fetchLatest() }.fold(
            onSuccess = { update -> update?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate(System.currentTimeMillis()) },
            onFailure = { UpdateState.Failed(null, "Couldn't check for updates. Check your internet connection.") },
        )
        _state.value = result
        result
    }

    private suspend fun fetchLatest(): AvailableUpdate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_RELEASE_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        http.newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful -> throw IOException("GitHub answered ${response.code}")
                else -> Releases.newerThan(installedVersion, response.body.string())
            }
        }
    }

    /** True when the banner for [update] was put off less than three days ago. */
    fun isPutOff(update: AvailableUpdate, now: Long = System.currentTimeMillis()): Boolean =
        settings.updatePutOffVersion == update.versionName &&
            now - settings.updatePutOffAt < TimeUnit.DAYS.toMillis(3)

    fun putOff(update: AvailableUpdate, now: Long = System.currentTimeMillis()) {
        settings.putOffUpdate(update.versionName, now)
    }

    /** Whether Android lets this app install updates, which she turns on once. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Downloads and installs [update]. Keeps going if she leaves the screen. */
    fun start(update: AvailableUpdate) {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing) return
        _state.value = UpdateState.Downloading(update, 0f)
        scope.launch {
            runCatchingCancellable {
                val file = download(update)
                verify(file)
                _state.value = UpdateState.Installing(update)
                install(file)
            }.onFailure { error ->
                // Kept for Copy details in Settings, like Android's install errors.
                settings.updateProblem = "Download of ${update.versionName} failed: " +
                    (error.message ?: error::class.java.simpleName)
                _state.value = UpdateState.Failed(
                    update,
                    (error as? UpdateException)?.message ?: "The download didn't finish. Check your internet connection and try again.",
                )
            }
        }
    }

    private fun download(update: AvailableUpdate): File {
        downloadFolder.deleteRecursively()
        downloadFolder.mkdirs()
        val file = File(downloadFolder, "recipe-box-${update.versionName}.apk")
        val request = Request.Builder().url(update.apkUrl).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download answered ${response.code}")
            val total = response.body.contentLength().takeIf { it > 0 } ?: update.apkSize
            var copied = 0L
            var lastShown = 0f
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        val progress = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else 0f
                        if (progress - lastShown >= 0.01f) {
                            lastShown = progress
                            _state.value = UpdateState.Downloading(update, progress)
                        }
                    }
                }
            }
        }
        return file
    }

    /** Checks the download is a newer Recipe Box from the same signer before Android sees it. */
    @Suppress("DEPRECATION")
    private fun verify(file: File) {
        val pm = context.packageManager
        val signingFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archive = pm.getPackageArchiveInfo(file.path, signingFlag)
            ?: throw UpdateException("The download is damaged. Try again.")
        if (archive.packageName != context.packageName) throw UpdateException("The download isn't Recipe Box.")
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(installed)) {
            throw UpdateException("The download isn't newer than the installed version.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val own = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val ownSigners = own.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            val newSigners = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
            if (ownSigners.isNullOrEmpty() || ownSigners != newSigners) {
                throw UpdateException("The download isn't signed like this app, so it wasn't installed.")
            }
        }
    }

    private fun install(file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            // Android skips its confirmation screen only when this app installed the current
            // version itself. The first time, Obtainium owns updates, so Android asks her.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("recipe-box.apk", 0, file.length()).use { output ->
                file.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val intent = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
            // Android adds the result to this intent, so it must be mutable.
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
    }

    internal fun onNeedsConfirmation(confirm: Intent) {
        val current = _state.value as? UpdateState.Installing ?: return
        _state.value = current.copy(confirm = confirm)
    }

    /** Android's own words for the last update that didn't install, for Settings to show. */
    val lastProblem: String? get() = settings.updateProblem

    /** [verificationFailure] is Android's developer verification reason, or -1. */
    internal fun onInstallFailed(status: Int, message: String?, verificationFailure: Int = -1) {
        val update = (_state.value as? UpdateState.Installing)?.update
        settings.updateProblem = buildString {
            append("Android answered ").append(STATUS_NAMES[status] ?: "status $status")
            if (!message.isNullOrBlank()) append(": ").append(message)
            if (verificationFailure >= 0) append(" (developer verification reason $verificationFailure)")
        }
        if (verificationFailure >= 0) {
            _state.value = UpdateState.Failed(
                update,
                if (verificationFailure == PackageInstaller.DEVELOPER_VERIFICATION_FAILED_REASON_NETWORK_UNAVAILABLE) {
                    "Android couldn't check the update while offline. Try again with an internet connection."
                } else {
                    "Android blocked the update because it couldn't verify the app's developer."
                },
            )
            return
        }
        val text = when {
            // Play Protect warns about apps that aren't from the Play Store, and its
            // Install anyway button is hidden until she opens More details. Tapping Got it
            // reports "aborted: INSTALL_FAILED_VERIFICATION_FAILURE", so check the message first.
            status == PackageInstaller.STATUS_FAILURE_BLOCKED || message?.contains("VERIFICATION") == true ->
                "Play Protect stopped the update. Tap Try again, then on its warning tap More details and Install anyway."
            // Cancel on Android's screen reports this too, but so do blocks by security settings,
            // so it isn't treated as a choice to skip the update.
            status == PackageInstaller.STATUS_FAILURE_ABORTED ->
                "The update was canceled or blocked. Tap Try again, or download it from GitHub."
            status == PackageInstaller.STATUS_FAILURE_STORAGE ->
                "There isn't enough free space on the phone for the update."
            else -> "Android didn't install the update. Tap Try again, or download it from GitHub."
        }
        _state.value = UpdateState.Failed(update, text)
    }

    private class UpdateException(message: String) : Exception(message)

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Isaiah-Yoder/Recipe-Box/releases/latest"

        /** The release page, where she can download the APK herself if installing from the app fails. */
        const val RELEASE_PAGE_URL = "https://github.com/Isaiah-Yoder/Recipe-Box/releases/latest"

        private val STATUS_NAMES = mapOf(
            PackageInstaller.STATUS_FAILURE to "failure",
            PackageInstaller.STATUS_FAILURE_BLOCKED to "blocked",
            PackageInstaller.STATUS_FAILURE_ABORTED to "aborted",
            PackageInstaller.STATUS_FAILURE_INVALID to "invalid",
            PackageInstaller.STATUS_FAILURE_CONFLICT to "conflict",
            PackageInstaller.STATUS_FAILURE_STORAGE to "storage",
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE to "incompatible",
            PackageInstaller.STATUS_FAILURE_TIMEOUT to "timeout",
        )
    }
}

/** Receives Android's answer about an update install. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updater = context.appContainer.updater
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                updater.onNeedsConfirmation(confirm)
                // Opens Android's install screen. If she left the app, the banner offers it instead.
                runCatching { context.startActivity(confirm) }
            }
            // On success Android replaces and restarts the app, so there's nothing to do.
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> updater.onInstallFailed(
                status,
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
                // Developer verification reports a failure as a cancel with a reason. Older Android
                // versions never add the extra, so reading it there gives -1.
                intent.getIntExtra(PackageInstaller.EXTRA_DEVELOPER_VERIFICATION_FAILURE_REASON, -1),
            )
        }
    }
}
