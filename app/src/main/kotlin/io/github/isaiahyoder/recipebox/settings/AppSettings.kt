package io.github.isaiahyoder.recipebox.settings

import io.github.isaiahyoder.recipebox.diagnostics.StallReport
import io.github.isaiahyoder.recipebox.ingredients.UnitSystem
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Progress of reading saved recipes' pages again. [failed] lists recipes whose page couldn't be read. */
@Serializable
data class RefreshStatus(
    val running: Boolean = false,
    val pending: List<Long> = emptyList(),
    val total: Int = 0,
    val updated: Int = 0,
    val failed: List<Long> = emptyList(),
    val finishedAt: Long = 0,
    /** An automatic run after an app update waits for Wi-Fi; one she starts uses any connection. */
    val wifiOnly: Boolean = false,
) {
    val done: Int get() = total - pending.size
}

data class DriveStatus(
    val enabled: Boolean = false,
    /** When the last backup succeeded or was checked as unchanged; 0 when never. */
    val lastSuccess: Long = 0,
    val lastError: String? = null,
    /** True when the last error was that she must connect Google Drive again. */
    val needsReconnect: Boolean = false,
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

    /**
     * Values that must not leave the phone, such as her Gemini key. Android's
     * automatic backup leaves this file out (res/xml/backup_rules.xml and
     * data_extraction_rules.xml).
     */
    private val secrets = context.getSharedPreferences("$fileName-$SECRETS_SUFFIX", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        prefs.getString(KEY_THEME, null)?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
    )
    /** Light or dark appearance. SYSTEM follows the phone's own setting. */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString(KEY_THEME, mode.name) }
        _themeMode.value = mode
    }

    private val _unitSystem = MutableStateFlow(
        prefs.getString(KEY_UNITS, null)?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: UnitSystem.US
    )

    /** The units recipes and new grocery lists show by default. A recipe can switch while she views it. */
    val unitSystem: StateFlow<UnitSystem> = _unitSystem.asStateFlow()

    fun setUnitSystem(units: UnitSystem) {
        prefs.edit { putString(KEY_UNITS, units.name) }
        _unitSystem.value = units
    }

    private val _driveStatus = MutableStateFlow(readDriveStatus())
    val driveStatus: StateFlow<DriveStatus> = _driveStatus.asStateFlow()

    fun setDriveEnabled(enabled: Boolean, now: Long = System.currentTimeMillis()) {
        prefs.edit {
            putBoolean(KEY_DRIVE_ENABLED, enabled)
            if (enabled) putLong(KEY_DRIVE_ENABLED_AT, now)
            remove(KEY_DRIVE_ERROR)
            remove(KEY_DRIVE_NEEDS_RECONNECT)
        }
        _driveStatus.value = readDriveStatus()
    }

    fun recordDriveSuccess(now: Long) {
        prefs.edit {
            putLong(KEY_DRIVE_SUCCESS, now)
            putLong(KEY_DRIVE_ATTEMPT, now)
            remove(KEY_DRIVE_ERROR)
            remove(KEY_DRIVE_NEEDS_RECONNECT)
        }
        _driveStatus.value = readDriveStatus()
    }

    fun recordDriveFailure(now: Long, message: String, needsReconnect: Boolean = false) {
        prefs.edit {
            putLong(KEY_DRIVE_ATTEMPT, now)
            putString(KEY_DRIVE_ERROR, message)
            putBoolean(KEY_DRIVE_NEEDS_RECONNECT, needsReconnect)
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

    private val _geminiKey = MutableStateFlow(readGeminiKey())

    /** Reads the key, moving one saved before 0.5.2 out of the backed-up settings. */
    private fun readGeminiKey(): String? {
        prefs.getString(KEY_GEMINI, null)?.let { old ->
            if (old.isNotBlank() && secrets.getString(KEY_GEMINI, null) == null) {
                secrets.edit(commit = true) { putString(KEY_GEMINI, old.trim()) }
            }
            prefs.edit { remove(KEY_GEMINI) }
        }
        return secrets.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }
    }

    /**
     * Her own Gemini API key for reading recipe cards, or null. It stays on
     * this phone: it's never in the app's backup files or Android's backup.
     */
    val geminiKey: StateFlow<String?> = _geminiKey.asStateFlow()

    fun setGeminiKey(key: String?) {
        val cleaned = key?.trim()?.takeIf { it.isNotEmpty() }
        secrets.edit { if (cleaned == null) remove(KEY_GEMINI) else putString(KEY_GEMINI, cleaned) }
        _geminiKey.value = cleaned
    }

    private val _refreshStatus = MutableStateFlow(
        prefs.getString(KEY_REFRESH, null)?.let { runCatching { json.decodeFromString<RefreshStatus>(it) }.getOrNull() } ?: RefreshStatus()
    )
    val refreshStatus: StateFlow<RefreshStatus> = _refreshStatus.asStateFlow()

    @Synchronized
    fun setRefreshStatus(status: RefreshStatus) {
        prefs.edit { putString(KEY_REFRESH, json.encodeToString(status)) }
        _refreshStatus.value = status
    }

    /** Changes the refresh status in one step, so a batch and Stop can't overwrite each other. */
    @Synchronized
    fun updateRefreshStatus(change: (RefreshStatus) -> RefreshStatus) = setRefreshStatus(change(_refreshStatus.value))

    private val _stallReports = MutableStateFlow(
        prefs.getString(KEY_STALLS, null)
            ?.let { runCatching { json.decodeFromString<List<StallReport>>(it) }.getOrNull() }
            ?: emptyList()
    )

    /** The last few times the screen stopped answering, newest first. */
    val stallReports: StateFlow<List<StallReport>> = _stallReports.asStateFlow()

    @Synchronized
    fun recordStall(report: StallReport) {
        val kept = (listOf(report) + _stallReports.value).take(MAX_STALLS)
        prefs.edit { putString(KEY_STALLS, json.encodeToString(kept)) }
        _stallReports.value = kept
    }

    @Synchronized
    fun clearStalls() {
        prefs.edit { remove(KEY_STALLS) }
        _stallReports.value = emptyList()
    }

    /** Why connecting Google Drive last failed, or null after a success. */
    private val _driveConnectProblem = MutableStateFlow(prefs.getString(KEY_DRIVE_CONNECT_PROBLEM, null))
    val driveConnectProblem: StateFlow<String?> = _driveConnectProblem.asStateFlow()

    fun setDriveConnectProblem(problem: String?) {
        prefs.edit { if (problem == null) remove(KEY_DRIVE_CONNECT_PROBLEM) else putString(KEY_DRIVE_CONNECT_PROBLEM, problem) }
        _driveConnectProblem.value = problem
    }

    /**
     * The tag rules and reading rules the saved recipes were last processed
     * with. When an update raises [io.github.isaiahyoder.recipebox.ContentVersions], the app
     * applies the new rules once.
     */
    var tagRulesVersion: Int
        get() = prefs.getInt(KEY_TAG_RULES_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_TAG_RULES_VERSION, value) }

    var readingVersion: Int
        get() = prefs.getInt(KEY_READING_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_READING_VERSION, value) }

    var ingredientIndexVersion: Int
        get() = prefs.getInt(KEY_INGREDIENT_INDEX_VERSION, 0)
        set(value) = prefs.edit { putInt(KEY_INGREDIENT_INDEX_VERSION, value) }

    /** Whether she has seen the offer to fill her categories from tags. */
    var feederOfferSeen: Boolean
        get() = prefs.getBoolean(KEY_FEEDER_OFFER, false)
        set(value) = prefs.edit { putBoolean(KEY_FEEDER_OFFER, value) }

    /** What Android said about the last update that didn't install, or null. */
    var updateProblem: String?
        get() = prefs.getString(KEY_UPDATE_PROBLEM, null)
        set(value) = prefs.edit { putString(KEY_UPDATE_PROBLEM, value) }

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
        // Versions before 0.6.0 stored only the English message, which asked her to reconnect.
        needsReconnect = if (prefs.contains(KEY_DRIVE_NEEDS_RECONNECT)) {
            prefs.getBoolean(KEY_DRIVE_NEEDS_RECONNECT, false)
        } else {
            prefs.getString(KEY_DRIVE_ERROR, null)?.contains("reconnect", ignoreCase = true) == true
        },
        lastAttempt = prefs.getLong(KEY_DRIVE_ATTEMPT, 0),
        enabledAt = prefs.getLong(KEY_DRIVE_ENABLED_AT, 0),
    )

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_UNITS = "unit_system"
        const val KEY_DRIVE_ENABLED = "drive_enabled"
        const val KEY_DRIVE_ENABLED_AT = "drive_enabled_at"
        const val KEY_DRIVE_SUCCESS = "drive_last_success"
        const val KEY_DRIVE_ATTEMPT = "drive_last_attempt"
        const val KEY_DRIVE_ERROR = "drive_last_error"
        const val KEY_DRIVE_NEEDS_RECONNECT = "drive_needs_reconnect"
        const val KEY_DRIVE_FOLDER = "drive_folder_id"
        const val KEY_DRIVE_PHOTO_FOLDER = "drive_photo_folder_id"
        const val KEY_DRIVE_HASH = "drive_last_hash"
        const val KEY_UPDATE_PUT_OFF_VERSION = "update_put_off_version"
        const val KEY_UPDATE_PUT_OFF_AT = "update_put_off_at"
        const val KEY_GEMINI = "gemini_api_key"
        const val KEY_REFRESH = "recipe_refresh_status"
        const val KEY_UPDATE_PROBLEM = "update_problem"
        const val KEY_DRIVE_CONNECT_PROBLEM = "drive_connect_problem"
        const val KEY_TAG_RULES_VERSION = "tag_rules_version"
        const val KEY_READING_VERSION = "reading_version"
        const val KEY_INGREDIENT_INDEX_VERSION = "ingredient_index_version"
        const val KEY_FEEDER_OFFER = "feeder_offer_seen"
        const val KEY_STALLS = "stall_reports"
        const val SECRETS_SUFFIX = "secrets"
        const val MAX_STALLS = 5
        val json = Json { ignoreUnknownKeys = true }
    }
}
