package com.carlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Одно событие может быть сразу ТО, ремонтом и тюнингом. Тип выводится из содержимого,
 * а `partsCost` раскладывается по категориям статистики — правило 8 CLAUDE.md в чистом виде,
 * поэтому граничные случаи проверяем здесь, а не глазами.
 */
class EventCompositionTest {

    private val to = MaintenanceType.SCHEDULED_SERVICE
    private val repair = MaintenanceType.REPAIR
    private val tuning = MaintenanceType.MODIFICATION

    // --- Типы события ---

    @Test
    fun `расходники делают событие ТО`() {
        assertEquals(
            listOf(to),
            EventComposition.typesOf(fallbackType = null, hasConsumables = true, partTypes = emptyList())
        )
    }

    @Test
    fun `расходники плюс запчасти дают несколько типов в фиксированном порядке`() {
        val types = EventComposition.typesOf(
            fallbackType = null,
            hasConsumables = true,
            partTypes = listOf(tuning, repair)
        )

        // Порядок всегда ТО → Ремонт → Тюнинг, независимо от порядка позиций
        assertEquals(listOf(to, repair, tuning), types)
    }

    @Test
    fun `одинаковые типы позиций не дублируются`() {
        assertEquals(
            listOf(repair),
            EventComposition.typesOf(null, hasConsumables = false, partTypes = listOf(repair, repair, repair))
        )
    }

    @Test
    fun `без позиций берётся выбранный руками тип`() {
        assertEquals(
            listOf(tuning),
            EventComposition.typesOf(fallbackType = tuning, hasConsumables = false, partTypes = emptyList())
        )
    }

    @Test
    fun `без позиций и без типа событие считается ремонтом`() {
        // Так же трактуются старые записи, заведённые до появления типов
        assertEquals(
            listOf(repair),
            EventComposition.typesOf(fallbackType = null, hasConsumables = false, partTypes = emptyList())
        )
    }

    @Test
    fun `позиции важнее выбранного типа`() {
        // Тип выбрали, пока позиций не было, потом добавили расходник — событие стало ТО
        assertEquals(
            listOf(to),
            EventComposition.typesOf(fallbackType = repair, hasConsumables = true, partTypes = emptyList())
        )
    }

    @Test
    fun `запчасть без типа считается ремонтом`() {
        assertEquals(repair, EventComposition.typeOfPart(null))
        assertEquals(repair, EventComposition.typeOfPart("ЧУШЬ"))
        assertEquals(tuning, EventComposition.typeOfPart("MODIFICATION"))
    }

    // --- Раскладка денег ---

    @Test
    fun `смешанное событие раскладывается по позициям`() {
        val split = EventComposition.split(
            partsCost = 28_100.0,
            fallbackType = null,
            consumableCosts = listOf(4_200.0),
            partPrices = listOf(repair to 8_900.0, tuning to 15_000.0)
        )

        assertEquals(4_200.0, split.scheduledService, 0.001)
        assertEquals(8_900.0, split.repair, 0.001)
        assertEquals(15_000.0, split.modification, 0.001)
        assertEquals(28_100.0, split.total, 0.001)
    }

    @Test
    fun `событие без позиций целиком уходит в свой тип`() {
        // Диагностика или запчасти общей суммой — считается ровно как до 1.2.5
        val split = EventComposition.split(
            partsCost = 5_000.0,
            fallbackType = tuning,
            consumableCosts = emptyList(),
            partPrices = emptyList()
        )

        assertEquals(EventCostSplit(modification = 5_000.0), split)
    }

    @Test
    fun `старое ТО без привязанных расходников остаётся в категории ТО`() {
        val split = EventComposition.split(
            partsCost = 7_000.0,
            fallbackType = to,
            consumableCosts = emptyList(),
            partPrices = emptyList()
        )

        assertEquals(7_000.0, split.scheduledService, 0.001)
    }

    @Test
    fun `расхождение позиций со стоимостью события уходит в запасную категорию`() {
        // Цену запчасти поправили в модуле «Запчасти», событие с тех пор не пересохраняли
        val split = EventComposition.split(
            partsCost = 10_000.0,
            fallbackType = repair,
            consumableCosts = emptyList(),
            partPrices = listOf(repair to 9_000.0)
        )

        // Итог обязан сойтись со стоимостью события, иначе цифры в статистике
        // разъедутся с карточкой при первом же обновлении приложения
        assertEquals(10_000.0, split.total, 0.001)
        assertEquals(10_000.0, split.repair, 0.001)
    }

    @Test
    fun `позиции дороже стоимости события не ломают итог`() {
        val split = EventComposition.split(
            partsCost = 8_000.0,
            fallbackType = repair,
            consumableCosts = emptyList(),
            partPrices = listOf(tuning to 10_000.0)
        )

        assertEquals(8_000.0, split.total, 0.001)
        assertEquals(10_000.0, split.modification, 0.001)
        assertEquals(-2_000.0, split.repair, 0.001)
    }

    @Test
    fun `запчасть с типом ТО остаётся в категории ТО`() {
        // Бывает у события, которому когда-то сменили тип на ТО: деньги двигать нельзя
        val split = EventComposition.split(
            partsCost = 3_000.0,
            fallbackType = to,
            consumableCosts = emptyList(),
            partPrices = listOf(to to 3_000.0)
        )

        assertEquals(3_000.0, split.scheduledService, 0.001)
        assertEquals(0.0, split.repair, 0.001)
    }

    @Test
    fun `расходник без цены считается за ноль`() {
        val split = EventComposition.split(
            partsCost = 1_000.0,
            fallbackType = to,
            consumableCosts = listOf(1_000.0, null),
            partPrices = emptyList()
        )

        assertEquals(1_000.0, split.scheduledService, 0.001)
        assertEquals(1_000.0, split.total, 0.001)
    }

    @Test
    fun `суммы событий складываются`() {
        val a = EventCostSplit(scheduledService = 100.0, repair = 200.0)
        val b = EventCostSplit(repair = 50.0, modification = 300.0)

        assertEquals(EventCostSplit(100.0, 250.0, 300.0), a + b)
    }
}
