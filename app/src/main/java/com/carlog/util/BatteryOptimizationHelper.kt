package com.carlog.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Исключение приложения из оптимизации батареи Android.
 *
 * Что это даёт: Doze откладывает фоновые задачи и режет фоновый доступ к сети, пока телефон
 * лежит с погашенным экраном, — из-за этого заливка копии может встать и оборваться по таймауту.
 * Исключение снимает это ограничение.
 *
 * Что это **не** даёт: приложение от этого ничего не начинает делать само по себе. Периодических
 * задач и будильников в проекте нет (только `OneTimeWorkRequest` в ответ на изменение данных),
 * так что в фоне выполняться по-прежнему нечему. Если когда-нибудь появятся push-напоминания
 * о расходниках и документах (роадмап, техдок §17) — решение стоит пересмотреть.
 *
 * Собственные менеджеры фона производителей (Huawei, Xiaomi) это исключение не отменяет —
 * сознательно не трогаем их недокументированные экраны.
 */
object BatteryOptimizationHelper {

    /** Уже разрешено работать в фоне без ограничений? Разрешений для проверки не требует. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean = try {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } catch (e: Exception) {
        // Проверка не должна ронять экран настроек; считаем, что ограничение есть
        false
    }

    /**
     * Показывает системный диалог «Разрешить работу в фоне без ограничений?».
     * Если показать его не удалось — открывает штатный список настроек батареи.
     *
     * @return false — не открылось ни то, ни другое
     */
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean {
        val dialog = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        if (tryStart(context, dialog)) return true

        // Фоллбэк: список приложений и их режим энергосбережения, разрешений не требует
        return tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        // Диалог — обычная активность: из не-Activity контекста ей нужна своя задача
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        // ActivityNotFoundException на прошивках без этого экрана
        false
    }
}
