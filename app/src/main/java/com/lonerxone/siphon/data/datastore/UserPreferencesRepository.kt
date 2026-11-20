package com.lonerxone.siphon.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "siphon_settings")

data class UserPreferences(
    val sdFolderUri: String? = null,
    val concurrencyLimit: Int = 2,
    val speedLimitKbps: Long = 0L,
    val allowMeteredWifi: Boolean = false,
    val autoAllowMobileDataMb: Long? = null,
    val subfolderOrganizeMode: String = "BY_SITE", // BY_SITE, BY_TYPE, FLAT
    val lastUsedFormat: String? = null
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val KEY_SD_FOLDER_URI = stringPreferencesKey("sd_folder_uri")
        val KEY_CONCURRENCY_LIMIT = intPreferencesKey("concurrency_limit")
        val KEY_SPEED_LIMIT_KBPS = longPreferencesKey("speed_limit_kbps")
        val KEY_ALLOW_METERED_WIFI = booleanPreferencesKey("allow_metered_wifi")
        val KEY_AUTO_ALLOW_MOBILE_MB = longPreferencesKey("auto_allow_mobile_mb")
        val KEY_ORGANIZE_MODE = stringPreferencesKey("organize_mode")
        val KEY_LAST_USED_FORMAT = stringPreferencesKey("last_used_format")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data.map { preferences ->
        UserPreferences(
            sdFolderUri = preferences[KEY_SD_FOLDER_URI],
            concurrencyLimit = (preferences[KEY_CONCURRENCY_LIMIT] ?: 2).coerceIn(1, 3),
            speedLimitKbps = preferences[KEY_SPEED_LIMIT_KBPS] ?: 0L,
            allowMeteredWifi = preferences[KEY_ALLOW_METERED_WIFI] ?: false,
            autoAllowMobileDataMb = preferences[KEY_AUTO_ALLOW_MOBILE_MB],
            subfolderOrganizeMode = preferences[KEY_ORGANIZE_MODE] ?: "BY_SITE",
            lastUsedFormat = preferences[KEY_LAST_USED_FORMAT]
        )
    }

    suspend fun setSdFolderUri(uri: String) {
        dataStore.edit { it[KEY_SD_FOLDER_URI] = uri }
    }

    suspend fun setConcurrencyLimit(limit: Int) {
        dataStore.edit { it[KEY_CONCURRENCY_LIMIT] = limit.coerceIn(1, 3) }
    }

    suspend fun setSpeedLimit(kbps: Long) {
        dataStore.edit { it[KEY_SPEED_LIMIT_KBPS] = kbps }
    }

    suspend fun setAllowMeteredWifi(allow: Boolean) {
        dataStore.edit { it[KEY_ALLOW_METERED_WIFI] = allow }
    }

    suspend fun setAutoAllowMobileMb(mb: Long?) {
        dataStore.edit {
            if (mb == null) {
                it.remove(KEY_AUTO_ALLOW_MOBILE_MB)
            } else {
                it[KEY_AUTO_ALLOW_MOBILE_MB] = mb
            }
        }
    }

    suspend fun setOrganizeMode(mode: String) {
        dataStore.edit { it[KEY_ORGANIZE_MODE] = mode }
    }

    suspend fun setLastUsedFormat(format: String) {
        dataStore.edit { it[KEY_LAST_USED_FORMAT] = format }
    }
}
