package com.carlog.presentation.screens.breakdowns

import com.carlog.domain.model.WorkItem
import com.carlog.domain.model.totalCost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Стоимость работ обслуживания: в режиме списка её источник — сами работы.
 * Именно эта сумма уходит в `Breakdown.serviceCost`, поэтому категория «Услуги сервисов»
 * в статистике продолжает считаться по одному полю и задвоиться не может (§5 бизнес-логики).
 */
class ServiceCostTest {

    private fun state(
        isServiceMaintenance: Boolean = true,
        useGeneralServiceCost: Boolean = false,
        serviceCost: String = "",
        workItems: List<WorkItem> = emptyList()
    ) = AddBreakdownState(
        isServiceMaintenance = isServiceMaintenance,
        useGeneralServiceCost = useGeneralServiceCost,
        serviceCost = serviceCost,
        workItems = workItems
    )

    private val works = listOf(
        WorkItem(name = "Замена масла", cost = 1_200.0),
        WorkItem(name = "Развал-схождение", cost = 3_000.0, notes = "передняя ось")
    )

    @Test
    fun `стоимость работ — сумма списка`() {
        assertEquals(4_200.0, works.totalCost(), 0.0001)
        assertEquals(4_200.0, state(workItems = works).calculatedServiceCost!!, 0.0001)
    }

    @Test
    fun `в режиме общей суммы список работ не участвует`() {
        val value = state(
            useGeneralServiceCost = true,
            serviceCost = "5000",
            workItems = works
        ).calculatedServiceCost

        assertEquals("введённая сумма важнее оставшегося в форме списка", 5_000.0, value!!, 0.0001)
    }

    /**
     * Регрессия: сначала стоимость работ обнулялась, если снята галочка «в сервисе».
     * У записей из прежних версий она бывает проставлена и без галочки — редактирование
     * такой записи молча уменьшало бы её общую стоимость.
     */
    @Test
    fun `в режиме общей суммы значение сохраняется и без галочки «в сервисе»`() {
        val value = state(
            isServiceMaintenance = false,
            useGeneralServiceCost = true,
            serviceCost = "3000"
        ).calculatedServiceCost

        assertEquals(3_000.0, value!!, 0.0001)
    }

    @Test
    fun `пустое поле стоимости работ остаётся пустым`() {
        assertNull(
            state(useGeneralServiceCost = true, serviceCost = "").calculatedServiceCost
        )
    }

    @Test
    fun `пустой список работ даёт ноль, а не мусор`() {
        assertEquals(0.0, state(workItems = emptyList()).calculatedServiceCost!!, 0.0001)
    }
}
