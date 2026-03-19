package com.echo.echocalendar

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureSettings {
    private const val KEY_MIGRATION_DONE = "__secure_settings_migration_done__"

    fun getPreferences(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encryptedPrefs = EncryptedSharedPreferences.create(
            appContext,
            SettingsKeys.SETTINGS_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        migrateLegacyPreferences(appContext, encryptedPrefs)
        return encryptedPrefs
    }

    private fun migrateLegacyPreferences(context: Context, encryptedPrefs: SharedPreferences) {
        if (encryptedPrefs.getBoolean(KEY_MIGRATION_DONE, false)) {
            return
        }
        val legacyPrefs = context.getSharedPreferences(
            SettingsKeys.LEGACY_SETTINGS_PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val legacyValues = legacyPrefs.all
        val editor = encryptedPrefs.edit()
        for ((key, value) in legacyValues) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Float -> editor.putFloat(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.putBoolean(KEY_MIGRATION_DONE, true)
        editor.commit()
        if (legacyValues.isNotEmpty()) {
            legacyPrefs.edit().clear().commit()
        }
    }
}
