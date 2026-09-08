package com.carlog.data.backup

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Вспомогательный класс для уведомления о изменениях данных
 * Используется в ViewModel после каждой операции изменения данных
 */
@Singleton
class DataChangeNotifier @Inject constructor(
    private val backupScheduler: BackupScheduler
) {
    private val _dataVersion = MutableStateFlow(0L)

    /**
     * Счётчик изменений данных. Растёт при **любой** мутации, в отличие от
     * `BackupScheduler.lastChangeTimestamp`, который обновляется только при включённом
     * автобэкапе и подключённом Диске и потому как общий сигнал не годится.
     *
     * Нужен экранам, которые считают производные величины и живут в бэкстеке, пока
     * пользователь правит данные на другом экране (см. `StatisticsViewModel`).
     */
    val dataVersion: StateFlow<Long> = _dataVersion.asStateFlow()

    /**
     * Уведомить о том, что данные изменились
     * Вызывать после: insert, update, delete операций
     */
    fun notifyDataChanged() {
        // update, а не присваивание: вызовы приходят из разных корутин и потоков
        _dataVersion.update { it + 1 }
        backupScheduler.notifyDataChanged()
    }
}
