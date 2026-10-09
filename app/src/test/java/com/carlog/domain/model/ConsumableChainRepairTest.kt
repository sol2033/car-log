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

    /**
     * Путь восстановления после бага с позицией задним числом: правка ТО на 6 000 км
     * вытеснила реальный расходник (установлен на 75 000) в «заменённые» и сделала
     * активной себя. Достаточно убрать лишнюю позицию из того же ТО — предшественник
     * находится по совпадению замены с установкой удаляемой позиции, и порядок пробегов
     * здесь роли не играет: «предшественник» на 75 000 старше удаляемого на 6 000.
     */
    @Test
    fun `удаление позиции задним числом возвращает вытесненный ею расходник`() {
        val real = consumable(
            id = 1,
            isActive = false,
            installationDate = 8_000,
            installationMileage = 75_000,
            replacementDate = 1_000,
            replacementMileage = 6_000
        )
        val bogus = consumable(id = 2, installationDate = 1_000, installationMileage = 6_000)

        val target = ConsumableChainRepair.reactivationTarget(listOf(real, bogus), bogus)

        assertEquals(1L, target?.id)
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

    // endregion

    // region insertPlan

    /**
     * Баг из обращения пользователя: правка ТО на 6 000 км при текущем пробеге 80 000
     * объявляла текущее масло заменённым и вела отсчёт ресурса от 6 000 —
     * весь экран расходников становился красным.
     */
    @Test
    fun `позиция задним числом не отбирает активность у свежей`() {
        val current = consumable(id = 1, installationDate = 8_000, installationMileage = 75_000)

        val plan = ConsumableChainRepair.insertPlan(
            sameCategory = listOf(current),
            category = "Масло в двигателе",
            installationMileage = 6_000,
            installationDate = 1_000
        )

        assertFalse("активной остаётся установка на 75 000", plan.isActive)
        assertEquals(75_000, plan.replacementMileage)
        assertEquals(8_000L, plan.replacementDate)
        assertTrue("текущий расходник трогать нельзя", plan.replaced.isEmpty())
    }

    @Test
    fun `новейшая позиция закрывает активную`() {
        val current = consumable(id = 1, installationDate = 1_000, installationMileage = 20_000)

        val plan = ConsumableChainRepair.insertPlan(
            sameCategory = listOf(current),
            category = "Масло в двигателе",
            installationMileage = 30_000,
            installationDate = 2_000
        )

        assertTrue(plan.isActive)
        assertNull(plan.replacementMileage)
        assertNull(plan.replacementDate)
        assertEquals(listOf(1L), plan.replaced.map { it.id })
    }

    @Test
    fun `вставка в середину цепочки переводит замену предшественника на себя`() {
        val first = consumable(
            id = 1,
            isActive = false,
            installationDate = 1_000,
            installationMileage = 10_000,
            replacementDate = 3_000,
            replacementMileage = 40_000
        )
        val last = consumable(id = 2, installationDate = 3_000, installationMileage = 40_000)

        val plan = ConsumableChainRepair.insertPlan(
            sameCategory = listOf(first, last),
            category = "Масло в двигателе",
            installationMileage = 25_000,
            installationDate = 2_000
        )

        assertFalse(plan.isActive)
        assertEquals("заменена следующей установкой", 40_000, plan.replacementMileage)
        assertEquals(
            "предшественник теперь заменён не последней позицией, а этой",
            listOf(1L),
            plan.replaced.map { it.id }
        )
    }

    @Test
    fun `первая позиция категории активна`() {
        val other = consumable(id = 1, category = "Фильтр масляный", installationMileage = 70_000)

        val plan = ConsumableChainRepair.insertPlan(
            sameCategory = listOf(other),
            category = "Масло в двигателе",
            installationMileage = 6_000,
            installationDate = 1_000
        )

        assertTrue("чужая категория цепочке не мешает", plan.isActive)
        assertTrue(plan.replaced.isEmpty())
    }

    @Test
    fun `разовая позиция «Другое» цепочку не строит`() {
        val sealant = consumable(
            id = 1,
            category = ConsumableCategories.OTHER,
            installationDate = 8_000,
            installationMileage = 75_000
        )

        val plan = ConsumableChainRepair.insertPlan(
            sameCategory = listOf(sealant),
            category = ConsumableCategories.OTHER,
            installationMileage = 6_000,
            installationDate = 1_000
        )

        assertTrue(plan.isActive)
        assertNull(plan.replacementMileage)
        assertTrue("герметик не «заменяет» прошлый герметик", plan.replaced.isEmpty())
    }

    // endregion

    // region активность в категории

    /** Реальная и активная — на 75 000; фиктивная, вытеснившая её, — на 6 000 */
    private val corrupted = listOf(
        consumable(
            id = 1,
            isActive = false,
            installationDate = 8_000,
            installationMileage = 75_000,
            replacementDate = 1_000,
            replacementMileage = 6_000
        ),
        consumable(id = 2, isActive = true, installationDate = 1_000, installationMileage = 6_000)
    )

    @Test
    fun `перестановка активности находится`() {
        val stale = ConsumableChainRepair.staleActive(corrupted)

        assertEquals(2L, stale?.active?.id)
        assertEquals(1L, stale?.newest?.id)
    }

    @Test
    fun `нормальная категория находкой не считается`() {
        val chain = listOf(
            consumable(
                id = 1,
                isActive = false,
                installationDate = 1_000,
                installationMileage = 10_000,
                replacementDate = 8_000,
                replacementMileage = 75_000
            ),
            consumable(id = 2, installationDate = 8_000, installationMileage = 75_000)
        )

        assertNull(ConsumableChainRepair.staleActive(chain))
    }

    @Test
    fun `два активных — дело другого правила`() {
        val chain = listOf(
            consumable(id = 1, installationDate = 1_000, installationMileage = 10_000),
            consumable(id = 2, installationDate = 8_000, installationMileage = 75_000)
        )

        assertNull(
            "иначе на одну категорию пришлись бы две находки",
            ConsumableChainRepair.staleActive(chain)
        )
    }

    @Test
    fun `у «Другого» активности не проверяем`() {
        val chain = corrupted.map { it.copy(category = ConsumableCategories.OTHER) }

        assertNull(ConsumableChainRepair.staleActive(chain))
    }

    @Test
    fun `починка возвращает активность свежей записи и закрывает вытеснившую`() {
        val changed = ConsumableChainRepair.repairActivity(corrupted, updatedAt = 99_000)
            .associateBy { it.id }

        val real = changed.getValue(1L)
        assertTrue("свежая установка снова активна", real.isActive)
        assertNull(real.replacementMileage)
        assertNull(real.replacementDate)

        val bogus = changed.getValue(2L)
        assertFalse(bogus.isActive)
        assertEquals("закрыта установкой на 75 000", 75_000, bogus.replacementMileage)
        assertEquals(8_000L, bogus.replacementDate)
        assertEquals(99_000L, bogus.updatedAt)
    }

    @Test
    fun `здоровую цепочку починка не трогает`() {
        val chain = listOf(
            consumable(
                id = 1,
                isActive = false,
                installationDate = 1_000,
                installationMileage = 10_000,
                replacementDate = 2_000,
                replacementMileage = 20_000
            ),
            consumable(
                id = 2,
                isActive = false,
                installationDate = 2_000,
                installationMileage = 20_000,
                replacementDate = 3_000,
                replacementMileage = 40_000
            ),
            consumable(id = 3, installationDate = 3_000, installationMileage = 40_000)
        )

        assertTrue(ConsumableChainRepair.repairActivity(chain, updatedAt = 99_000).isEmpty())
    }

    /**
     * Дату снятия пользователь мог ввести сам, и она законно не совпадает с установкой
     * преемника (слил масло на 45 000, залил новое на 46 000). Затирать её ради красоты
     * цепочки нельзя — правятся только невозможные значения.
     */
    @Test
    fun `осмысленный пробег замены починка не переписывает`() {
        val chain = listOf(
            consumable(
                id = 1,
                isActive = false,
                installationDate = 1_000,
                installationMileage = 40_000,
                replacementDate = 4_500,
                replacementMileage = 45_000
            ),
            consumable(id = 2, installationDate = 4_600, installationMileage = 46_000)
        )

        assertTrue(ConsumableChainRepair.repairActivity(chain, updatedAt = 99_000).isEmpty())
    }

    @Test
    fun `невозможный пробег замены починка исправляет`() {
        val chain = listOf(
            consumable(
                id = 1,
                isActive = false,
                installationDate = 1_000,
                installationMileage = 40_000,
                replacementDate = 4_500,
                replacementMileage = 90_000
            ),
            consumable(id = 2, installationDate = 4_600, installationMileage = 46_000)
        )

        val changed = ConsumableChainRepair.repairActivity(chain, updatedAt = 99_000)

        assertEquals(1, changed.size)
        assertEquals("замена не может быть позже установки преемника", 46_000, changed.first().replacementMileage)
        assertEquals(4_600L, changed.first().replacementDate)
    }

    // endregion

    // region что можно вернуть при удалении (§4.4 бизнес-логики)

    /** Масло: старое на 10 000 заменено новым на 20 000 */
    private val oldOil = consumable(
        id = 1, isActive = false, installationDate = 1_000, installationMileage = 10_000,
        replacementDate = 2_000, replacementMileage = 20_000
    )
    private val newOil = consumable(id = 2, installationDate = 2_000, installationMileage = 20_000)

    @Test
    fun `удаление текущего расходника предлагает вернуть предыдущий`() {
        val restorable = ConsumableChainRepair.restorableOnDelete(listOf(oldOil, newOil), listOf(newOil))

        assertEquals(listOf(1L), restorable.map { it.predecessor.id })
        assertEquals(2L, restorable.single().removed.id)
    }

    /** Удаление записи из истории категорию не опустошает — спрашивать не о чем */
    @Test
    fun `удаление заменённого расходника ничего не предлагает`() {
        val older = consumable(
            id = 0, isActive = false, installationDate = 500, installationMileage = 5_000,
            replacementDate = 1_000, replacementMileage = 10_000
        )

        assertTrue(ConsumableChainRepair.restorableOnDelete(listOf(older, oldOil, newOil), listOf(oldOil)).isEmpty())
    }

    @Test
    fun `первая установка категории — возвращать некого`() {
        val only = consumable(id = 5, installationDate = 1_000, installationMileage = 10_000)

        assertTrue(ConsumableChainRepair.restorableOnDelete(listOf(only), listOf(only)).isEmpty())
    }

    /** ТО с маслом и фильтром: у масла предшественник есть, у фильтра — нет */
    @Test
    fun `удаление ТО предлагает вернуть только тех, у кого есть предшественник`() {
        val filter = consumable(id = 3, category = "Фильтр масляный", installationDate = 2_000, installationMileage = 20_000)

        val restorable = ConsumableChainRepair.restorableOnDelete(
            listOf(oldOil, newOil, filter),
            listOf(newOil, filter)
        )

        assertEquals(listOf("Масло в двигателе"), restorable.map { it.removed.category })
    }

    @Test
    fun `возвращённый предшественник активен и без замены`() {
        val back = ConsumableChainRepair.reactivated(oldOil, updatedAt = 9_999)

        assertTrue(back.isActive)
        assertNull(back.replacementDate)
        assertNull(back.replacementMileage)
        assertEquals(9_999L, back.updatedAt)
        assertEquals(oldOil.installationMileage, back.installationMileage)
    }

    // endregion
}
