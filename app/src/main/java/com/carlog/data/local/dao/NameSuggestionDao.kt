package com.carlog.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.carlog.domain.model.ServiceAddress
import kotlinx.coroutines.flow.Flow

/**
 * Названия, которые пользователь уже вводил, — источник подсказок в формах
 * (см. [com.carlog.domain.model.NameSuggestions]).
 *
 * Своих таблиц у DAO нет: он только читает чужие. Списки — по **всем** машинам: сервис
 * и заправка от автомобиля не зависят. Порядок — по дате последнего использования, недавние
 * первыми; регистр и дубли окончательно схлопывает `NameSuggestions.suggest`.
 */
@Dao
interface NameSuggestionDao {

    /**
     * Сервисы — общий список из обслуживания, запчастей и прочих расходов: один и тот же
     * сервис встречается во всех трёх местах, и раздельные подсказки только мешали бы.
     */
    @Query("""
        SELECT TRIM(name) FROM (
            SELECT serviceName AS name, breakdownDate AS usedAt FROM breakdowns
            UNION ALL
            SELECT serviceName AS name, installDate AS usedAt FROM parts
            UNION ALL
            SELECT serviceName AS name, date AS usedAt FROM expenses
        )
        WHERE name IS NOT NULL AND TRIM(name) != ''
        GROUP BY TRIM(name)
        ORDER BY MAX(usedAt) DESC
    """)
    fun getServiceNames(): Flow<List<String>>

    /**
     * Пары «сервис — адрес» из всех трёх модулей, самые недавние первыми. Совпадение
     * по названию ищет `NameSuggestions.addressFor` — без учёта регистра, что SQLite
     * для кириллицы не умеет.
     */
    @Query("""
        SELECT name, address FROM (
            SELECT serviceName AS name, serviceAddress AS address, breakdownDate AS usedAt FROM breakdowns
            UNION ALL
            SELECT serviceName AS name, serviceAddress AS address, installDate AS usedAt FROM parts
            UNION ALL
            SELECT serviceName AS name, serviceAddress AS address, date AS usedAt FROM expenses
        )
        WHERE name IS NOT NULL AND TRIM(name) != '' AND address IS NOT NULL AND TRIM(address) != ''
        ORDER BY usedAt DESC
    """)
    suspend fun getServiceAddresses(): List<ServiceAddress>

    /** Заправки и зарядные станции — одно поле `stationName` у заправок */
    @Query("""
        SELECT TRIM(stationName) FROM refuelings
        WHERE stationName IS NOT NULL AND TRIM(stationName) != ''
        GROUP BY TRIM(stationName)
        ORDER BY MAX(date) DESC
    """)
    fun getStationNames(): Flow<List<String>>
}
