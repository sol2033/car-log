package com.carlog.data.backup

/**
 * Ключи `SharedPreferences("backup_prefs")` в одном месте.
 * Значения строк менять нельзя — они уже лежат на устройствах пользователей.
 */
object BackupPrefs {
    const val NAME = "backup_prefs"

    const val KEY_TOKEN = "yandex_token"
    const val KEY_AUTO_BACKUP = "auto_backup_enabled"
    const val KEY_AUTO_DELETE = "auto_delete_old_backups"
    const val KEY_LAST_BACKUP = "last_backup_timestamp"

    /** Когда данные меняли в последний раз — по нему догоняющий бэкап понимает, что есть что сохранять */
    const val KEY_LAST_CHANGE = "last_data_change_timestamp"

    /** Заливать только по Wi-Fi */
    const val KEY_WIFI_ONLY = "backup_wifi_only"

    /** Журнал последних событий бэкапа */
    const val KEY_LOG = "backup_log"

    /** Пользователь закрыл подсказку про оптимизацию батареи */
    const val KEY_BATTERY_HINT_DISMISSED = "battery_hint_dismissed"
}
