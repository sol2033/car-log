package com.carlog.data.backup

import androidx.annotation.StringRes
import com.carlog.R

/**
 * Текст для пользователя по категории ошибки.
 *
 * Вынесено из [BackupErrorType] отдельно, чтобы сама классификация оставалась чистой
 * и тестировалась на JVM без ресурсов Android (правило 9).
 */
@StringRes
fun BackupErrorType.messageRes(): Int = when (this) {
    BackupErrorType.NO_NETWORK -> R.string.backup_error_no_network
    BackupErrorType.TIMEOUT -> R.string.backup_error_timeout
    BackupErrorType.SERVER -> R.string.backup_error_server
    BackupErrorType.NOT_CONNECTED -> R.string.backup_error_not_connected
    BackupErrorType.UNAUTHORIZED -> R.string.backup_error_unauthorized
    BackupErrorType.QUOTA_EXCEEDED -> R.string.backup_error_quota
    BackupErrorType.NOT_FOUND -> R.string.backup_error_not_found
    BackupErrorType.UNKNOWN -> R.string.backup_error_unknown
}
