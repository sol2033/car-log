package com.carlog.util

import android.content.Context
import android.content.Intent

/**
 * Полный перезапуск приложения (новый процесс).
 *
 * Нужен после восстановления/импорта БД: Activity.recreate() не перезапускает процесс —
 * Hilt-синглтоны и ViewModel'ы переживают его и продолжают показывать данные,
 * загруженные до подмены файла базы.
 */
object AppRestart {

    fun restart(context: Context) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val componentName = launchIntent?.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(componentName))
        // Система поднимет новый процесс для запущенной задачи
        Runtime.getRuntime().exit(0)
    }
}
