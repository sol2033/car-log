package com.carlog.data.backup

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
    /**
     * Уведомить о том, что данные изменились
     * Вызывать после: insert, update, delete операций
     */
    fun notifyDataChanged() {
        backupScheduler.notifyDataChanged()
    }
}
