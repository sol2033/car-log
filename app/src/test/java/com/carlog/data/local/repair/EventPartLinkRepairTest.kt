package com.carlog.data.local.repair

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила автофикса связей «событие → запчасти» (миграция 19→20).
 * Главное требование: чинить только однозначное, сомнительное не трогать вовсе —
 * ошибочная привязка молча меняет суммы в статистике.
 */
class EventPartLinkRepairTest {

    private fun breakdown(
        id: Long,
        mileage: Int = 80_000,
        date: Long = 1_000,
        partsCost: Double = 7_500.0,
        maintenanceType: String? = "REPAIR",
        alreadyLinked: Boolean = false
    ) = RepairEvent(
        id = id,
        carId = 1,
        date = date,
        mileage = mileage,
        maintenanceType = maintenanceType,
        partsCost = partsCost,
        alreadyLinked = alreadyLinked
    )

    private fun accident(
        id: Long,
        mileage: Int = 70_000,
        date: Long = 1_000,
        alreadyLinked: Boolean = false
    ) = RepairEvent(id = id, carId = 1, date = date, mileage = mileage, alreadyLinked = alreadyLinked)

    private fun part(
        id: Long,
        price: Double,
        mileage: Int = 80_000,
        date: Long = 1_000,
        installationType: String = "Сервис",
        maintenanceType: String? = "REPAIR",
        claimed: Boolean = false,
        carId: Long = 1
    ) = RepairPart(
        id = id,
        carId = carId,
        installDate = date,
        installMileage = mileage,
        installationType = installationType,
        maintenanceType = maintenanceType,
        price = price,
        claimed = claimed
    )

    // --- Обслуживания ---

    @Test
    fun `связь восстанавливается, когда сумма запчастей точно совпала`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 7_500.0)),
            listOf(part(1, 5_000.0), part(2, 2_500.0))
        )

        assertEquals(mapOf(40L to listOf(1L, 2L)), links)
    }

    @Test
    fun `копеечное расхождение сумм допустимо`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 7_500.0)),
            listOf(part(1, 2_500.005), part(2, 4_999.995))
        )

        assertEquals(mapOf(40L to listOf(1L, 2L)), links)
    }

    @Test
    fun `сумма не сошлась — не трогаем`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 7_500.0)),
            listOf(part(1, 5_000.0))
        )

        assertTrue("набор неполный или содержит чужое", links.isEmpty())
    }

    @Test
    fun `два события с одинаковой датой и пробегом — не угадываем`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 5_000.0), breakdown(id = 41, partsCost = 5_000.0)),
            listOf(part(1, 5_000.0))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `запчасть без типа обслуживания добавлена вручную — не привязываем`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 5_000.0)),
            listOf(part(1, 5_000.0, maintenanceType = null))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `тип обслуживания запчасти не совпадает с типом события — не привязываем`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 5_000.0, maintenanceType = "SCHEDULED_SERVICE")),
            listOf(part(1, 5_000.0, maintenanceType = "REPAIR"))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `запчасть другого способа установки не рассматривается`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 5_000.0)),
            listOf(part(1, 5_000.0, installationType = "Самостоятельно"))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `занятую запчасть не забираем у другого события`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 5_000.0)),
            listOf(part(1, 5_000.0, claimed = true))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `событие с уже целой связью пропускается`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 5_000.0, alreadyLinked = true)),
            listOf(part(1, 5_000.0))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `запчасть другой машины не рассматривается`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 5_000.0)),
            listOf(part(1, 5_000.0, carId = 2))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `нулевая стоимость запчастей ничего не подтверждает`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(breakdown(id = 40, partsCost = 0.0)),
            listOf(part(1, 0.0))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `разные события в разные дни чинятся независимо`() {
        val links = EventPartLinkRepair.resolveBreakdownLinks(
            listOf(
                breakdown(id = 40, date = 1_000, partsCost = 5_000.0),
                breakdown(id = 41, date = 2_000, partsCost = 3_000.0)
            ),
            listOf(
                part(1, 5_000.0, date = 1_000),
                part(2, 3_000.0, date = 2_000)
            )
        )

        assertEquals(mapOf(40L to listOf(1L), 41L to listOf(2L)), links)
    }

    // --- ДТП ---

    @Test
    fun `запчасти ДТП привязываются без проверки суммы`() {
        val links = EventPartLinkRepair.resolveAccidentLinks(
            listOf(accident(id = 50)),
            listOf(part(1, 9_000.0, mileage = 70_000, installationType = "ДТП", maintenanceType = null))
        )

        assertEquals(mapOf(50L to listOf(1L)), links)
    }

    @Test
    fun `две аварии в один день и пробег — не угадываем`() {
        val links = EventPartLinkRepair.resolveAccidentLinks(
            listOf(accident(id = 50), accident(id = 51)),
            listOf(part(1, 9_000.0, mileage = 70_000, installationType = "ДТП", maintenanceType = null))
        )

        assertTrue(links.isEmpty())
    }

    @Test
    fun `сервисная запчасть не уходит в ДТП`() {
        val links = EventPartLinkRepair.resolveAccidentLinks(
            listOf(accident(id = 50)),
            listOf(part(1, 9_000.0, mileage = 70_000, installationType = "Сервис"))
        )

        assertTrue(links.isEmpty())
    }
}
