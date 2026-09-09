package com.asok.medrecall.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asok.medrecall.data.calendar.DeviceCalendarInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.settingsDataStore by preferencesDataStore(name = "medrecall_settings")

/** The one device calendar (Google, Office/Exchange, or local) appointments are currently synced to, if any. */
data class SyncCalendarSelection(
    val calendarId: Long,
    val displayName: String,
    val providerLabel: String
)

/** Settings > Account's Google Drive row -- see [SettingsRepository.googleAccount]. */
data class GoogleAccountSelection(
    val email: String,
    val displayName: String?,
    val driveConnected: Boolean
)

/**
 * Settings > Account's OneDrive row -- see [SettingsRepository.microsoftAccount].
 * Unlike Google (separate sign-in + Drive-authorization steps), MSAL grants
 * the Files.ReadWrite scope in the same interactive call as sign-in, so
 * there's no separate "driveConnected" flag here -- a non-null selection
 * means OneDrive access is already granted.
 */
data class MicrosoftAccountSelection(
    val email: String,
    val displayName: String?
)

/**
 * Backs the Settings > Security screen (app-lock) and the Settings >
 * Calendar Sync row (which device calendar, if any, appointments sync to --
 * see data/calendar/DeviceCalendarManager.kt). Nothing here is ever synced
 * anywhere by MedRecall itself -- it's local DataStore only, same as the
 * rest of MedRecall's data.
 *
 * The PIN is never stored in plaintext -- only a SHA-256 hash salted with a
 * random per-install value, so reading this DataStore file directly (e.g.
 * an adb backup) doesn't reveal the PIN.
 */
class SettingsRepository private constructor(private val context: Context) {

    private object Keys {
        val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val SYNC_CALENDAR_ID = longPreferencesKey("sync_calendar_id")
        val SYNC_CALENDAR_NAME = stringPreferencesKey("sync_calendar_name")
        val SYNC_CALENDAR_PROVIDER = stringPreferencesKey("sync_calendar_provider")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val GOOGLE_ACCOUNT_NAME = stringPreferencesKey("google_account_name")
        val GOOGLE_DRIVE_CONNECTED = booleanPreferencesKey("google_drive_connected")
        val MICROSOFT_ACCOUNT_EMAIL = stringPreferencesKey("microsoft_account_email")
        val MICROSOFT_ACCOUNT_NAME = stringPreferencesKey("microsoft_account_name")
    }

    val biometricLockEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.BIOMETRIC_LOCK_ENABLED] ?: false }

    val pinEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.PIN_ENABLED] ?: false }

    /** True if either lock method is on -- this is what actually gates app launch. */
    val appLockEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            (prefs[Keys.BIOMETRIC_LOCK_ENABLED] ?: false) || (prefs[Keys.PIN_ENABLED] ?: false)
        }

    /** Null means calendar sync is off. */
    val syncCalendar: Flow<SyncCalendarSelection?> =
        context.settingsDataStore.data.map { prefs ->
            val id = prefs[Keys.SYNC_CALENDAR_ID] ?: return@map null
            SyncCalendarSelection(
                calendarId = id,
                displayName = prefs[Keys.SYNC_CALENDAR_NAME] ?: "Calendar",
                providerLabel = prefs[Keys.SYNC_CALENDAR_PROVIDER] ?: ""
            )
        }

    /**
     * Null means no Google account is connected under Settings > Account.
     * Only identity (email/name) and a connected flag are stored here --
     * OAuth access tokens are short-lived and kept in memory only (see
     * GoogleAccountManager), never written to disk.
     */
    val googleAccount: Flow<GoogleAccountSelection?> =
        context.settingsDataStore.data.map { prefs ->
            val email = prefs[Keys.GOOGLE_ACCOUNT_EMAIL] ?: return@map null
            GoogleAccountSelection(
                email = email,
                displayName = prefs[Keys.GOOGLE_ACCOUNT_NAME],
                driveConnected = prefs[Keys.GOOGLE_DRIVE_CONNECTED] ?: false
            )
        }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.BIOMETRIC_LOCK_ENABLED] = enabled }
    }

    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hashPin(pin, salt)
            prefs[Keys.PIN_SALT] = salt.toHex()
            prefs[Keys.PIN_ENABLED] = true
        }
    }

    suspend fun clearPin() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.PIN_HASH)
            prefs.remove(Keys.PIN_SALT)
            prefs[Keys.PIN_ENABLED] = false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val prefs = context.settingsDataStore.data.first()
        val storedHash = prefs[Keys.PIN_HASH] ?: return false
        val saltHex = prefs[Keys.PIN_SALT] ?: return false
        return hashPin(pin, saltHex.fromHex()) == storedHash
    }

    suspend fun setSyncCalendar(calendar: DeviceCalendarInfo) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.SYNC_CALENDAR_ID] = calendar.id
            prefs[Keys.SYNC_CALENDAR_NAME] = calendar.displayName
            prefs[Keys.SYNC_CALENDAR_PROVIDER] = calendar.providerLabel
        }
    }

    suspend fun clearSyncCalendar() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.SYNC_CALENDAR_ID)
            prefs.remove(Keys.SYNC_CALENDAR_NAME)
            prefs.remove(Keys.SYNC_CALENDAR_PROVIDER)
        }
    }

    /** Records a successful Google sign-in. Call [setGoogleDriveConnected] separately once Drive authorization also succeeds. */
    suspend fun setGoogleAccount(email: String, displayName: String?) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.GOOGLE_ACCOUNT_EMAIL] = email
            displayName?.let { prefs[Keys.GOOGLE_ACCOUNT_NAME] = it }
        }
    }

    suspend fun setGoogleDriveConnected(connected: Boolean) {
        context.settingsDataStore.edit { prefs -> prefs[Keys.GOOGLE_DRIVE_CONNECTED] = connected }
    }

    /** Disconnects the Google account entirely (Settings > Account > Google Drive > Disconnect). */
    suspend fun clearGoogleAccount() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.GOOGLE_ACCOUNT_EMAIL)
            prefs.remove(Keys.GOOGLE_ACCOUNT_NAME)
            prefs.remove(Keys.GOOGLE_DRIVE_CONNECTED)
        }
    }

    /**
     * Null means no Microsoft account is connected under Settings > Account.
     * Only identity (email/name) is stored here -- OAuth access tokens are
     * short-lived and kept in memory only (see MicrosoftAccountManager),
     * never written to disk.
     */
    val microsoftAccount: Flow<MicrosoftAccountSelection?> =
        context.settingsDataStore.data.map { prefs ->
            val email = prefs[Keys.MICROSOFT_ACCOUNT_EMAIL] ?: return@map null
            MicrosoftAccountSelection(
                email = email,
                displayName = prefs[Keys.MICROSOFT_ACCOUNT_NAME]
            )
        }

    /** Records a successful Microsoft sign-in (identity + OneDrive access granted together, see MicrosoftAccountManager). */
    suspend fun setMicrosoftAccount(email: String, displayName: String?) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.MICROSOFT_ACCOUNT_EMAIL] = email
            if (displayName != null) prefs[Keys.MICROSOFT_ACCOUNT_NAME] = displayName else prefs.remove(Keys.MICROSOFT_ACCOUNT_NAME)
        }
    }

    /** Disconnects the Microsoft account entirely (Settings > Account > OneDrive > Disconnect). */
    suspend fun clearMicrosoftAccount() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.MICROSOFT_ACCOUNT_EMAIL)
            prefs.remove(Keys.MICROSOFT_ACCOUNT_NAME)
        }
    }

    /** Wipes every stored setting -- used by Danger Zone > Delete All Data. */
    suspend fun clearAll() {
        context.settingsDataStore.edit { it.clear() }
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        return digest.digest(pin.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
