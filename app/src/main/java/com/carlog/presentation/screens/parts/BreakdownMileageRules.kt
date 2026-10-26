package com.carlog.presentation.screens.parts

import com.carlog.domain.model.Part

/**
 * Пробег поломки в диалоге «Отметить сломанной».
 *
 * Раньше проверялось только «больше нуля»: пробег ниже пробега установки сохранялся,
 * и у запчасти появлялся отрицательный пройденный ресурс.
 */
internal fun isValidBreakdownMileage(input: String, part: Part): Boolean {
    val mileage = input.toIntOrNull() ?: return false
    return mileage > 0 && mileage >= part.installMileage
}

/** Введено число, но оно меньше пробега установки — показать подсказку у поля */
internal fun breakdownMileageBelowInstall(input: String, part: Part): Boolean {
    val mileage = input.toIntOrNull() ?: return false
    return mileage < part.installMileage
}
