package com.greenart7c3.nostrsigner

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

object DataStoreAccess {
    private val storeCache = ConcurrentHashMap<String, DataStore<Preferences>>()

    private fun getDataStore(context: Context, npub: String): DataStore<Preferences> {
        return storeCache.computeIfAbsent(npub) {
            Log.d(Amber.TAG, "Creating new DataStore for $npub")
            PreferenceDataStoreFactory.create(
                produceFile = { context.applicationContext.preferencesDataStoreFile("secure_datastore_$npub") },
            )
        }
    }

    val NOSTR_PRIVKEY = stringPreferencesKey("nostr_privkey")
    val SEED_WORDS = stringPreferencesKey("seed_words")

    suspend fun saveEncryptedKey(context: Context, npub: String, key: Preferences.Key<String>, value: String) {
        val encrypted = SecureCryptoHelper.encrypt(value)
        getDataStore(context, npub).edit { prefs ->
            prefs[key] = encrypted
        }
    }

    suspend fun getEncryptedKey(context: Context, npub: String, key: Preferences.Key<String>): String? {
        val prefs = getDataStore(context, npub).data
            .catch { e ->
                if (e is IOException) emit(emptyPreferences()) else throw e
            }
            .first()

        val encrypted = prefs[key] ?: return null
        return SecureCryptoHelper.decrypt(encrypted)
    }

    fun clearCacheForNpub(npub: String) {
        storeCache.remove(npub)
    }
}
