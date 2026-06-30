package net.helcel.cowspent.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

object SecureStorage {
    private const val SECURE_PREFS_NAME = "secure_prefs"
    private var encryptedPrefs: SharedPreferences? = null

    @Synchronized
    fun getEncryptedPrefs(context: Context): SharedPreferences {
        encryptedPrefs?.let { return it }
        
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        val prefs = EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        encryptedPrefs = prefs
        return prefs
    }

    fun savePassword(context: Context, key: String, password: String?) {
        if (password == null) {
            removePassword(context, key)
        } else {
            getEncryptedPrefs(context).edit { putString(key, password) }
        }
    }

    fun getPassword(context: Context, key: String): String? {
        return getEncryptedPrefs(context).getString(key, null)
    }

    fun removePassword(context: Context, key: String) {
        getEncryptedPrefs(context).edit { remove(key) }
    }
}
