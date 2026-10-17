package com.liomix.mikomi.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.liomix.mikomi.core.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: androidx.datastore.core.DataStore<Preferences> by
    preferencesDataStore(name = "user_preferences")

/** 用户偏好数据源（DataStore，进程内单例）。 */
@Singleton
class UserPreferencesDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.userPreferencesDataStore

    val userPreferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            theme = prefs[KEY_THEME] ?: DEFAULT_THEME,
            defaultQuality = prefs[KEY_QUALITY] ?: DEFAULT_QUALITY,
            autoPlayNext = prefs[KEY_AUTO_PLAY_NEXT] ?: true,
        )
    }

    suspend fun setTheme(theme: String) {
        dataStore.edit { it[KEY_THEME] = theme }
    }

    suspend fun setDefaultQuality(quality: String) {
        dataStore.edit { it[KEY_QUALITY] = quality }
    }

    suspend fun setAutoPlayNext(enabled: Boolean) {
        dataStore.edit { it[KEY_AUTO_PLAY_NEXT] = enabled }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_QUALITY = stringPreferencesKey("default_quality")
        val KEY_AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        const val DEFAULT_THEME = "system"
        const val DEFAULT_QUALITY = "auto"
    }
}
