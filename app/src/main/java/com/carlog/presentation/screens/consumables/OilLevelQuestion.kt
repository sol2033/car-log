package com.carlog.presentation.screens.consumables

import com.carlog.domain.model.Car
import com.carlog.domain.model.Consumable

/**
 * Форма вот-вот закроет моторное масло — сначала спрашиваем, насколько упал его уровень.
 * Общая для отдельной формы расходника и формы ТО: вопрос задаётся одинаково везде,
 * где масло заменяется (§4.4.1 бизнес-логики).
 */
data class OilLevelQuestion(
    val oldOil: Consumable,
    val car: Car
)
