package com.carlog.domain.model

/**
 * Цепочка замен расходника: кто кого заменил.
 *
 * Связи «заменил» в БД нет — есть только категория и совпадение даты и пробега замены
 * предшественника с датой и пробегом установки преемника. Этого достаточно, потому что
 * деактивацию всегда проставляет тот же код, что создаёт новую позицию
 * (`AddBreakdownViewModel`, «Заменить» на экране расходника).
 */
object ConsumableChainRepair {

    /**
     * Кого вернуть в активные, если [removed] удаляют из ТО.
     *
     * Возвращаем предшественника только тогда, когда после удаления в категории вообще
     * не остаётся активной позиции: если после этого ТО расходник уже меняли ещё раз,
     * активен более свежий, и воскрешать старый нельзя — в категории окажется два активных,
     * а это ровно то рассогласование, которое ищет экран «Проверка данных».
     *
     * @param all все расходники автомобиля (включая [removed])
     */
    fun reactivationTarget(all: List<Consumable>, removed: Consumable): Consumable? {
        // Разовые позиции «Другое» цепочки не образуют — им некого возвращать
        if (!ConsumableFormRules.supportsReminders(removed.category)) return null

        val sameCategory = all.filter { it.category == removed.category && it.id != removed.id }
        if (sameCategory.any { it.isActive }) return null

        return sameCategory
            .filter {
                !it.isActive &&
                    it.replacementDate == removed.installationDate &&
                    it.replacementMileage == removed.installationMileage
            }
            .maxWithOrNull(compareBy({ it.installationDate }, { it.id }))
    }

    /**
     * Кому поправить дату и пробег замены, если у [consumable] сдвинулись дата и пробег
     * установки (пользователь исправил дату самого ТО).
     *
     * Без этого предшественник остался бы «заменённым» задним числом относительно своей
     * замены, и связь между ними потерялась бы: [reactivationTarget] ищет её по совпадению.
     *
     * @param all все расходники автомобиля до изменения
     */
    fun predecessorToShift(all: List<Consumable>, consumable: Consumable): Consumable? {
        if (!ConsumableFormRules.supportsReminders(consumable.category)) return null
        return all
            .filter {
                it.id != consumable.id &&
                    it.category == consumable.category &&
                    !it.isActive &&
                    it.replacementDate == consumable.installationDate &&
                    it.replacementMileage == consumable.installationMileage
            }
            .maxWithOrNull(compareBy({ it.installationDate }, { it.id }))
    }

    /**
     * Куда встаёт новая позиция в цепочке замен своей категории.
     *
     * Позицию можно завести задним числом — заполняя давнее ТО или добавляя пропущенную
     * замену. Тогда активной в категории остаётся более свежая установка, а новая сразу
     * получает пробег и дату замены своего преемника.
     */
    data class ChainInsert(
        /** Новая позиция активна: свежее неё в категории ничего нет */
        val isActive: Boolean,
        /** Пробег замены новой позиции — установка преемника (null у активной) */
        val replacementMileage: Int?,
        /** Дата замены новой позиции — установка преемника (null у активной) */
        val replacementDate: Long?,
        /**
         * Кого эта установка закрывает: им проставляется замена её датой и пробегом.
         * У новейшей позиции это активные записи категории, у позиции задним числом —
         * предшественник, который раньше числился заменённым сразу преемником.
         */
        val replaced: List<Consumable>
    )

    /**
     * План вставки новой позиции в категорию.
     *
     * Без него добавление считалось самой свежей заменой всегда: исправление ТО на 6 000 км
     * объявляло текущий расходник заменённым и вело отсчёт ресурса от пробега того ТО —
     * на 80 000 км весь экран расходников становился красным.
     *
     * @param sameCategory расходники автомобиля (лишние категории отфильтровываются здесь)
     */
    fun insertPlan(
        sameCategory: List<Consumable>,
        category: String,
        installationMileage: Int,
        installationDate: Long
    ): ChainInsert {
        // Разовые позиции «Другое» цепочки не образуют: они никого не заменяют
        if (!ConsumableFormRules.supportsReminders(category)) {
            return ChainInsert(
                isActive = true,
                replacementMileage = null,
                replacementDate = null,
                replaced = emptyList()
            )
        }

        val chain = sameCategory.filter { it.category == category }
        // Порядок в цепочке — по пробегу: по нему же выбирает выжившего «Проверка данных».
        // Дата разводит две установки на одном пробеге
        val order = compareBy<Consumable>({ it.installationMileage }, { it.installationDate })
        fun isAfter(other: Consumable) =
            other.installationMileage > installationMileage ||
                (other.installationMileage == installationMileage &&
                    other.installationDate > installationDate)

        val successor = chain.filter { isAfter(it) }.minWithOrNull(order)
            ?: return ChainInsert(
                isActive = true,
                replacementMileage = null,
                replacementDate = null,
                replaced = chain.filter { it.isActive }
            )

        // Позиция задним числом встаёт перед преемником: активной она не становится,
        // а предшественник теперь заменён ею, а не сразу преемником
        val predecessor = chain
            .filterNot { isAfter(it) }
            .filter {
                it.replacementMileage == successor.installationMileage &&
                    it.replacementDate == successor.installationDate
            }
            .maxWithOrNull(order)

        return ChainInsert(
            isActive = false,
            replacementMileage = successor.installationMileage,
            replacementDate = successor.installationDate,
            replaced = listOfNotNull(predecessor)
        )
    }

    // region Активность в категории

    /**
     * Активна не самая свежая установка категории.
     *
     * Светофор расходника считается по активной записи, поэтому такая перестановка врёт
     * ресурсом на весь разрыв пробегов: правка ТО на 6 000 км при текущих 80 000 делала
     * активной установку с 6 000, и категория горела красным.
     */
    data class StaleActive(
        /** Запись, которая числится активной сейчас */
        val active: Consumable,
        /** Запись, которая должна быть активной: самая свежая установка категории */
        val newest: Consumable
    )

    /**
     * Порядок установок в категории: пробег, затем дата, затем id.
     *
     * Пробег первичен — по нему же выбирает выжившего `DataIntegrityChecker`; дата разводит
     * две установки на одном пробеге, id — две установки одного дня.
     */
    private val installationOrder =
        compareBy<Consumable>({ it.installationMileage }, { it.installationDate }, { it.id })

    /**
     * Находка «активна не самая свежая» для одной категории.
     *
     * Случай «активных несколько» сюда не относится: его ищет отдельное правило
     * `duplicateActiveConsumables`, и две находки на одну категорию были бы шумом.
     * У «Другого» активности нет смысла: это разовые позиции ТО, а не цепочка замен.
     *
     * @param sameCategory все расходники одной категории (другие отфильтровываются здесь)
     */
    fun staleActive(sameCategory: List<Consumable>): StaleActive? {
        val category = sameCategory.firstOrNull()?.category ?: return null
        if (!ConsumableFormRules.supportsReminders(category)) return null

        val chain = sameCategory.filter { it.category == category }
        val active = chain.singleOrNull { it.isActive } ?: return null
        val newest = chain.maxWithOrNull(installationOrder) ?: return null

        return if (newest.id == active.id) null else StaleActive(active = active, newest = newest)
    }

    /**
     * Приводит активность в категории к инварианту «активна самая свежая установка».
     *
     * Возвращает **только изменившиеся** записи — их и нужно сохранить.
     *
     * Даты и пробеги замены правятся сознательно осторожно: только там, где сохранённое
     * значение невозможно (его нет, оно раньше собственной установки или позже установки
     * преемника). Значение, которое просто не совпало с установкой следующей записи,
     * остаётся как есть — пользователь мог ввести реальную дату снятия, и затирать её
     * ради красоты цепочки нельзя.
     *
     * @param sameCategory все расходники одной категории
     */
    fun repairActivity(sameCategory: List<Consumable>, updatedAt: Long): List<Consumable> {
        val category = sameCategory.firstOrNull()?.category ?: return emptyList()
        if (!ConsumableFormRules.supportsReminders(category)) return emptyList()

        val ordered = sameCategory.filter { it.category == category }.sortedWith(installationOrder)
        if (ordered.isEmpty()) return emptyList()

        val changed = mutableListOf<Consumable>()

        // Самая свежая установка — активна, и «заменена» она быть не может
        val newest = ordered.last()
        if (!newest.isActive || newest.replacementMileage != null || newest.replacementDate != null) {
            changed += newest.copy(
                isActive = true,
                replacementMileage = null,
                replacementDate = null,
                updatedAt = updatedAt
            )
        }

        ordered.dropLast(1).forEachIndexed { index, current ->
            val successor = ordered[index + 1]
            val storedMileage = current.replacementMileage
            val impossible = storedMileage == null ||
                current.replacementDate == null ||
                storedMileage < current.installationMileage ||
                storedMileage > successor.installationMileage

            if (current.isActive || impossible) {
                changed += current.copy(
                    isActive = false,
                    replacementMileage =
                        if (impossible) successor.installationMileage else current.replacementMileage,
                    replacementDate =
                        if (impossible) successor.installationDate else current.replacementDate,
                    updatedAt = updatedAt
                )
            }
        }

        return changed
    }

    // endregion
}

