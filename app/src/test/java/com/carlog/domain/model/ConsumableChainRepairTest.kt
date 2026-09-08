package com.carlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Цепочка замен расходника держится на совпадении категории, даты и пробега — связи
 * «заменил» в БД нет. Поэтому её граничные случаи проверяются здесь.
 */
/**
 * Цепочка замен: кто кого заменил. Связи в БД нет — она восстанавливается по совпадению
 * категории, даты и пробега, поэтому правила проверяем отдельно.
 */
class ConsumableChainRepairTest {

    private fun consumable(
        id: Long,
        category: String = "Масло в двигателе",
        isActive: Boolean = true,
        installationDate: Long = 0L,
        installationMileage: Int = 0,
        replacementDate: Long? = null,
        replacementMileage: Int? = null
    ) = Consumable(
        id = id,
        carId = 1,
        category = category,
        manufacturer = null,
        articleNumber = null,
        installationMileage = installationMileage,
        installationDate = installationDate,
        replacementMileage = replacementMileage,
        replacementDate = replacementDate,
        cost = 1000.0,
        isInstalledAtService = false,
        serviceCost = null,
        volume = null,
        replacementIntervalMileage = null,
        replacementIntervalDays = null,
        isActive = isActive,
        notes = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    // region reactivationTarget

    @Test
    fun `удаление расходника из ТО возвращает в активные его предшественника`() {
        val predecessor = consumable(
            id = 1,
            isActive = false,
            installationDate = 100,
            installationMileage = 10_000,
            replacementDate = 200,
            replacementMileage = 20_000
        )
        val removed = consumable(id = 2, installationDate = 200, installationMileage = 20_000)

        val target = ConsumableChainRepair.reactivationTarget(listOf(predecessor, removed), removed)

        assertEquals(1L, target?.id)
    }

    @Test
    fun `если после ТО расходник меняли ещё раз, старый не воскресает`() {
        // Иначе в категории окажется два активных — ровно то рассогласование,
        // которое ищет экран «Проверка данных»
        val predecessor = consumable(
            id = 1,
            isActive = false,
            installationDate = 100,
            installationMileage = 10_000,
            replacementDate = 200,
            replacementMileage = 20_000
        )
        val removed = consumable(
            id = 2,
            isActive = false,
            installationDate = 200,
            installationMileage = 20_000,
            replacementDate = 300,
            replacementMileage = 30_000
        )
        val newer = consumable(id = 3, installationDate = 300, installationMileage = 30_000)

        val target = ConsumableChainRepair.reactivationTarget(
            listOf(predecessor, removed, newer),
            removed
        )

        assertNull(target)
    }

    @Test
    fun `предшественник из другой категории не берётся`() {
        val other = consumable(
            id = 1,
            category = "Фильтр масляный",
            isActive = false,
            installationDate = 100,
            replacementDate = 200,
            replacementMileage = 20_000
        )
        val removed = consumable(id = 2, installationDate = 200, installationMileage = 20_000)

        assertNull(ConsumableChainRepair.reactivationTarget(listOf(other, removed), removed))
    }

    @Test
    fun `предшественник с другой датой замены не берётся`() {
        // Он деактивирован не этим ТО, а чем-то ещё — трогать его нельзя
        val unrelated = consumable(
            id = 1,
            isActive = false,
            installationDate = 100,
            replacementDate = 150,
            replacementMileage = 15_000
        )
        val removed = consumable(id = 2, installationDate = 200, installationMileage = 20_000)

        assertNull(ConsumableChainRepair.reactivationTarget(listOf(unrelated, removed), removed))
    }

    @Test
    fun `разовая позиция Другое цепочки не образует`() {
        val earlier = consumable(
            id = 1,
            category = ConsumableCategories.OTHER,
            isActive = false,
            installationDate = 100,
            replacementDate = 200,
            replacementMileage = 20_000
        )
        val removed = consumable(
            id = 2,
            category = ConsumableCategories.OTHER,
            installationDate = 200,
            installationMileage = 20_000
        )

        assertNull(ConsumableChainRepair.reactivationTarget(listOf(earlier, removed), removed))
    }

    @Test
    fun `из нескольких подходящих берётся самый поздний`() {
        val old = consumable(
            id = 1,
            isActive = false,
            installationDate = 50,
            replacementDate = 200,
            replacementMileage = 20_000
        )
        val recent = consumable(
            id = 2,
            isActive = false,
            installationDate = 150,
            replacementDate = 200,
            replacementMileage = 20_000
        )
        val removed = consumable(id = 3, installationDate = 200, installationMileage = 20_000)

        val target = ConsumableChainRepair.reactivationTarget(
            listOf(old, recent, removed),
            removed
        )

        assertEquals(2L, target?.id)
    }

    // endregion

    // region predecessorToShift

    @Test
    fun `исправленная дата ТО двигает дату замены предшественника`() {
        val predecessor = consumable(
            id = 1,
            isActive = false,
            installationDate = 100,
            replacementDate = 200,
            replacementMileage = 20_000
        )
        val edited = consumable(id = 2, installationDate = 200, installationMileage = 20_000)

        val target = ConsumableChainRepair.predecessorToShift(listOf(predecessor, edited), edited)

        assertEquals(1L, target?.id)
    }

    @Test
    fun `активный расходник предшественником не считается`() {
        val active = consumable(id = 1, installationDate = 100)
        val edited = consumable(id = 2, installationDate = 200, installationMileage = 20_000)

        assertNull(ConsumableChainRepair.predecessorToShift(listOf(active, edited), edited))
    }
}
