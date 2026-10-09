package com.carlog.data.repository

import com.carlog.data.local.dao.NameSuggestionDao
import com.carlog.domain.model.NameSuggestions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Подсказки названий в формах: сервисы и заправки из прошлых записей.
 * Выдаёт готовый список под текущий ввод — формам остаётся только показать чипы.
 */
@Singleton
class NameSuggestionRepository @Inject constructor(
    private val nameSuggestionDao: NameSuggestionDao
) {

    fun serviceNameSuggestions(input: Flow<String>): Flow<List<String>> =
        combine(nameSuggestionDao.getServiceNames(), input) { known, typed ->
            NameSuggestions.suggest(known, typed)
        }

    /** Адрес сервиса из самой недавней записи с этим названием — для автоподстановки */
    suspend fun lastServiceAddress(serviceName: String): String? =
        NameSuggestions.addressFor(serviceName, nameSuggestionDao.getServiceAddresses())

    fun stationNameSuggestions(input: Flow<String>): Flow<List<String>> =
        combine(nameSuggestionDao.getStationNames(), input) { known, typed ->
            NameSuggestions.suggest(known, typed)
        }
}
