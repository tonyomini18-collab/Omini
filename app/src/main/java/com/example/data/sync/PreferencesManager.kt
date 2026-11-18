package com.example.data.sync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "aroma_pos_prefs")

data class LocalAuthSession(
    val uid: String,
    val displayName: String,
    val roleKey: String
)

class PreferencesManager(private val context: Context) {
    private val keyLanguage = stringPreferencesKey("language")
    private val keyDarkMode = booleanPreferencesKey("dark_mode")
    private val keyActiveStoreId = stringPreferencesKey("active_store_id")
    private val keyLastActivity = longPreferencesKey("last_activity_millis")
    private val keySeededPrefix = stringPreferencesKey("seeded_store_id")
    private val keyLocalUid = stringPreferencesKey("local_session_uid")
    private val keyLocalName = stringPreferencesKey("local_session_name")
    private val keyLocalRole = stringPreferencesKey("local_session_role")
    private val keyAdminUsername = stringPreferencesKey("admin_username")
    private val keyAdminPasswordHash = stringPreferencesKey("admin_password_hash")
    private val keyPcServerUrl = stringPreferencesKey("pc_server_url")
    private val keyLastPcSyncMillis = longPreferencesKey("last_pc_sync_millis")

    companion object {
        const val DEFAULT_ADMIN_UID = "local_admin_omini"
        const val DEFAULT_ADMIN_USERNAME = "omini"
        // SHA-256 of "admin116"
        const val DEFAULT_ADMIN_PASSWORD_HASH = "4b9cef74e2b8c8e29f5ef21ca0ce05ddfad983d37e45449eb00bac3f1ff0d70d"

        fun sha256(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(input.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyLanguage] ?: "ar"
    }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyDarkMode] ?: false
    }

    val activeStoreIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyActiveStoreId] ?: ""
    }

    val lastActivityFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[keyLastActivity] ?: System.currentTimeMillis()
    }

    val seededStoreIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keySeededPrefix] ?: ""
    }

    val localSessionFlow: Flow<LocalAuthSession?> = context.dataStore.data.map { prefs ->
        val uid = prefs[keyLocalUid]
        val name = prefs[keyLocalName]
        val role = prefs[keyLocalRole]
        if (!uid.isNullOrBlank() && !name.isNullOrBlank()) {
            LocalAuthSession(uid = uid, displayName = name, roleKey = role ?: "admin")
        } else {
            null
        }
    }

    suspend fun verifyAndSignInLocalAdmin(username: String, password: String): Boolean {
        val prefs = context.dataStore.data.first()
        val expectedUser = prefs[keyAdminUsername] ?: DEFAULT_ADMIN_USERNAME
        val expectedHash = prefs[keyAdminPasswordHash] ?: DEFAULT_ADMIN_PASSWORD_HASH
        val matches = username.trim().equals(expectedUser, ignoreCase = true) &&
            sha256(password) == expectedHash
        if (matches) {
            context.dataStore.edit { edit ->
                edit[keyLocalUid] = DEFAULT_ADMIN_UID
                edit[keyLocalName] = expectedUser
                edit[keyLocalRole] = "admin"
            }
        }
        return matches
    }

    suspend fun clearLocalSession() {
        context.dataStore.edit { edit ->
            edit.remove(keyLocalUid)
            edit.remove(keyLocalName)
            edit.remove(keyLocalRole)
        }
    }

    suspend fun setLanguage(code: String) {
        context.dataStore.edit { it[keyLanguage] = code }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[keyDarkMode] = enabled }
    }

    suspend fun setActiveStoreId(storeId: String) {
        context.dataStore.edit { it[keyActiveStoreId] = storeId }
    }

    suspend fun updateLastActivity(millis: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[keyLastActivity] = millis }
    }

    val pcServerUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyPcServerUrl] ?: ""
    }

    val lastPcSyncMillisFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[keyLastPcSyncMillis] ?: 0L
    }

    suspend fun setPcServerUrl(url: String) {
        context.dataStore.edit { it[keyPcServerUrl] = url.trim() }
    }

    suspend fun setLastPcSyncMillis(millis: Long) {
        context.dataStore.edit { it[keyLastPcSyncMillis] = millis }
    }

    suspend fun markStoreSeeded(storeId: String) {
        context.dataStore.edit { it[keySeededPrefix] = storeId }
    }
}
