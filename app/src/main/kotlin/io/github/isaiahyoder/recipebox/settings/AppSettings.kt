package io.github.isaiahyoder.recipebox.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class DriveStatus(
    val enabled: Boolean = false,
    /** When the last backup succeeded or was checked as unchanged; 0 when never. */
    val lastSuccess: Long = 0,
    val lastError: String? = null,
    val lastAttempt: Long = 0,
    val enabledAt: Long = 0,
) {
    /** True when backups are on but haven't worked for over a week. */
    fun isOverdue(now: Long): Boolean {
        if (!enabled) return false
        val since = if (lastSuccess > 0) lastSuccess else enabledAt
        return now - since > TimeUnit.DAYS.toMillis(7)
    }
}

/** Small app preferences that aren't recipe data. */
class AppSettings(context: Context, fileName: String = "settings") {
    private val prefs = context.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    )
    /** Light or dark appearance. SYSTEM follows the phone's own setting. */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    private val _driveStatus = MutableStateFlow(readDriveStatus())
    val driveStatus: StateFlow<DriveStatus> = _driveStatus.asStateFlow()

    fun setDriveEnabled(enabled: Boolean, now: Long = System.currentTimeMillis()) {
        prefs.edit {
            putBoolean(KEY_DRIVE_ENABLED, enabled)
            if (enabled) putLong(KEY_DRIVE_ENABLED_AT, now)
            remove(KEY_DRIVE_ERROR)
        }
        _driveStatus.value = readDriveStatus()
    }

    fun recordDriveSuccess(now: Long) {
        prefs.edit {
            putLong(KEY_DRIVE_SUCCESS, now)
            putLong(KEY_DRIVE_ATTEMPT, now)
            remove(KEY_DRIVE_ERROR)
        }
        _driveStatus.value = readDriveStatus()
    }

    fun recordDriveFailure(now: Long, message: String) {
        prefs.edit {
            putLong(KEY_DRIVE_ATTEMPT, now)
            putString(KEY_DRIVE_ERROR, message)
        }
        _driveStatus.value = readDriveStatus()
    }

    var driveFolderId: String?
        get() = prefs.getString(KEY_DRIVE_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_DRIVE_FOLDER, value) }

    var drivePhotoFolderId: String?
        get() = prefs.getString(KEY_DRIVE_PHOTO_FOLDER, null)
        set(value) = prefs.edit { putString(KEY_DRIVE_PHOTO_FOLDER, value) }

    /** Fingerprint of the last uploaded backup, to skip uploading an unchanged library. */
    var driveLastHash: String?
        get() = prefs.getString(KEY_DRIVE_HASH, null)
        set(value) = prefs.edit { putString(KEY_DRIVE_HASH, value) }

    /** The update version whose banner she put off, and when. */
    val updatePutOffVersion: String? get() = prefs.getString(KEY_UPDATE_PUT_OFF_VERSION, null)
    val updatePutOffAt: Long get() = prefs.getLong(KEY_UPDATE_PUT_OFF_AT, 0)

    fun putOffUpdate(version: String, now: Long) {
        prefs.edit {
            putString(KEY_UPDATE_PUT_OFF_VERSION, version)
            putLong(KEY_UPDATE_PUT_OFF_AT, now)
        }
    }

    private fun readDriveStatus() = DriveStatus(
        enabled = prefs.getBoolean(KEY_DRIVE_ENABLED, false),
        lastSuccess = prefs.getLong(KEY_DRIVE_SUCCESS, 0),
        lastError = prefs.getString(KEY_DRIVE_ERROR, null),
        lastAttempt = prefs.getLong(KEY_DRIVE_ATTEMPT, 0),
        enabledAt = prefs.getLong(KEY_DRIVE_ENABLED_AT, 0),
    )

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_DRIVE_ENABLED = "drive_enabled"
        const val KEY_DRIVE_ENABLED_AT = "drive_enabled_at"
        const val KEY_DRIVE_SUCCESS = "drive_last_success"
        const val KEY_DRIVE_ATTEMPT = "drive_last_attempt"
        const val KEY_DRIVE_ERROR = "drive_last_error"
        const val KEY_DRIVE_FOLDER = "drive_folder_id"
        const val KEY_DRIVE_PHOTO_FOLDER = "drive_photo_folder_id"
        const val KEY_DRIVE_HASH = "drive_last_hash"
        const val KEY_UPDATE_PUT_OFF_VERSION = "update_put_off_version"
        const val KEY_UPDATE_PUT_OFF_AT = "update_put_off_at"
    }
}
