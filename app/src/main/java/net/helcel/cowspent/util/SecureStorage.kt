package net.helcel.cowspent.util

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_prefs")

object SecureStorage {
    private var aead: Aead? = null

    @Synchronized
    private fun getAead(context: Context): Aead {
        aead?.let { return it }
        AeadConfig.register()
        val masterKeyUri = "android-keystore://secure_storage_master_key"
        val keysetManager = AndroidKeysetManager.Builder()
            .withSharedPref(context, "tink_keyset", "secure_storage_keyset_prefs")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(masterKeyUri)
            .build()
        val newAead = keysetManager.keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        aead = newAead
        return newAead
    }

    private fun encrypt(context: Context, value: String): String {
        val encrypted = getAead(context).encrypt(value.toByteArray(), null)
        return Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(context: Context, encryptedValue: String): String? {
        return try {
            val decoded = Base64.decode(encryptedValue, Base64.NO_WRAP)
            val decrypted = getAead(context).decrypt(decoded, null)
            String(decrypted)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun savePassword(context: Context, key: String, password: String?) {
        if (password == null) {
            removePassword(context, key)
        } else {
            val encrypted = encrypt(context, password)
            context.dataStore.edit { it[stringPreferencesKey(key)] = encrypted }
        }
    }

    suspend fun getPassword(context: Context, key: String): String? {
        val encrypted = context.dataStore.data.map { it[stringPreferencesKey(key)] }.first()
        return encrypted?.let { decrypt(context, it) }
    }

    suspend fun removePassword(context: Context, key: String) {
        context.dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    // Synchronous alternatives for legacy code
    fun savePasswordSync(context: Context, key: String, password: String?) = runBlocking {
        savePassword(context, key, password)
    }

    fun getPasswordSync(context: Context, key: String): String? = runBlocking {
        getPassword(context, key)
    }

    fun removePasswordSync(context: Context, key: String) = runBlocking {
        removePassword(context, key)
    }
}
