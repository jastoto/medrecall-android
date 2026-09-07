package com.asok.medrecall.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.settingsDataStore by preferencesDataStore(name = "medrecall_settings")

/**
 * Backs the Settings > Security screen: whether the app-lock (biometric
 * and/or PIN) is turned on, and the salted PIN hash itself. Nothing here
 * is ever synced anywhere -- it's local DataStore only, same as the rest
 * of MedRecall's data.
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
