package com.carlog.data.repository

import com.carlog.domain.model.ConsumableChainRepair

/**
 * Что показать в диалоге удаления ТО или расходника.
 *
 * @property removedCount сколько расходников удалится
 * @property restorable категории, которые без удаляемых останутся пустыми, и их предыдущие
 * установки — пользователь решает, вернуть их или оставить категории пустыми
 */
data class ConsumableDeletePlan(
    val removedCount: Int,
    val restorable: List<ConsumableChainRepair.Restorable>
)
