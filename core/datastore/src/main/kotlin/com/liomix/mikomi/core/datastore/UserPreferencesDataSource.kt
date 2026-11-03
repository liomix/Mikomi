package com.liomix.mikomi.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
            colorScheme = prefs[KEY_COLOR_SCHEME] ?: DEFAULT_COLOR_SCHEME,
            appIcon = prefs[KEY_APP_ICON] ?: DEFAULT_APP_ICON,
            transitionStyle = prefs[KEY_TRANSITION_STYLE] ?: DEFAULT_TRANSITION_STYLE,
            pageTransitionStyle = prefs[KEY_PAGE_TRANSITION_STYLE] ?: DEFAULT_TRANSITION_STYLE,
            bangumiApiMode = prefs[KEY_BANGUMI_API_MODE] ?: DEFAULT_BANGUMI_API_MODE,
            bangumiMirrorUrl = prefs[KEY_BANGUMI_MIRROR_URL] ?: "",
            playerDefaultSpeed = prefs[KEY_PLAYER_DEFAULT_SPEED] ?: DEFAULT_PLAYER_SPEED,
            playerLongPressSpeed = prefs[KEY_PLAYER_LONG_PRESS_SPEED] ?: DEFAULT_LONG_PRESS_SPEED,
            playerSeekSweepSeconds = prefs[KEY_PLAYER_SEEK_SWEEP_SECONDS] ?: DEFAULT_SEEK_SWEEP_SECONDS,
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

    suspend fun setColorScheme(colorScheme: String) {
        dataStore.edit { it[KEY_COLOR_SCHEME] = colorScheme }
    }

    suspend fun setAppIcon(appIcon: String) {
        dataStore.edit { it[KEY_APP_ICON] = appIcon }
    }

    suspend fun setTransitionStyle(style: String) {
        dataStore.edit { it[KEY_TRANSITION_STYLE] = style }
    }

    suspend fun setPageTransitionStyle(style: String) {
        dataStore.edit { it[KEY_PAGE_TRANSITION_STYLE] = style }
    }

    suspend fun setBangumiApiMode(mode: String) {
        dataStore.edit { it[KEY_BANGUMI_API_MODE] = mode }
    }

    suspend fun setBangumiMirrorUrl(url: String) {
        dataStore.edit { it[KEY_BANGUMI_MIRROR_URL] = url }
    }

    suspend fun setPlayerDefaultSpeed(speed: Float) {
        dataStore.edit { it[KEY_PLAYER_DEFAULT_SPEED] = speed }
    }

    suspend fun setPlayerLongPressSpeed(speed: Float) {
        dataStore.edit { it[KEY_PLAYER_LONG_PRESS_SPEED] = speed }
    }

    suspend fun setPlayerSeekSweepSeconds(seconds: Int) {
        dataStore.edit { it[KEY_PLAYER_SEEK_SWEEP_SECONDS] = seconds }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_QUALITY = stringPreferencesKey("default_quality")
        val KEY_AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
        val KEY_COLOR_SCHEME = stringPreferencesKey("color_scheme")
        val KEY_APP_ICON = stringPreferencesKey("app_icon")
        val KEY_TRANSITION_STYLE = stringPreferencesKey("transition_style")
        val KEY_PAGE_TRANSITION_STYLE = stringPreferencesKey("page_transition_style")
        val KEY_BANGUMI_API_MODE = stringPreferencesKey("bangumi_api_mode")
        val KEY_BANGUMI_MIRROR_URL = stringPreferencesKey("bangumi_mirror_url")
        val KEY_PLAYER_DEFAULT_SPEED = floatPreferencesKey("player_default_speed")
        val KEY_PLAYER_LONG_PRESS_SPEED = floatPreferencesKey("player_long_press_speed")
        val KEY_PLAYER_SEEK_SWEEP_SECONDS = intPreferencesKey("player_seek_sweep_seconds")
        const val DEFAULT_THEME = "system"
        const val DEFAULT_QUALITY = "auto"
        const val DEFAULT_COLOR_SCHEME = "default"
        const val DEFAULT_APP_ICON = "default"
        const val DEFAULT_TRANSITION_STYLE = "translate"
        const val DEFAULT_BANGUMI_API_MODE = "direct"
        const val DEFAULT_PLAYER_SPEED = 1f
        const val DEFAULT_LONG_PRESS_SPEED = 2f
        const val DEFAULT_SEEK_SWEEP_SECONDS = 90
    }
}
