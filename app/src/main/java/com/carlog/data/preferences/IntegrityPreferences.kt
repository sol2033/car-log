package com.carlog.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.integrityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "integrity_preferences"
)

/**
 * Находки проверки данных, которые пользователь пометил как «оставить как есть».
 * Без этого списка законно устроенная запись (например, действительно отдельная запчасть)
 * всплывала бы находкой при каждом входе на экран.
 */
@Singleton
class IntegrityPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.integrityDataStore

    private companion object {
        val IGNORED_FINDINGS = stringSetPreferencesKey("ignored_findings")
    }

    val ignoredFindings: Flow<Set<String>> = dataStore.data.map { preferences ->
        preferences[IGNORED_FINDINGS] ?: emptySet()
    }

    suspend fun ignore(findingId: String) {
        dataStore.edit { preferences ->
            preferences[IGNORED_FINDINGS] = (preferences[IGNORED_FINDINGS] ?: emptySet()) + findingId
        }
    }

    /** Возврат скрытых находок в список — на случай ошибочного нажатия */
    suspend fun clearIgnored() {
        dataStore.edit { preferences ->
            preferences.remove(IGNORED_FINDINGS)
        }
    }
}
