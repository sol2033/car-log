package com.carlog.domain.model

/** Сервис и его адрес из одной прошлой записи — источник автоподстановки адреса */
data class ServiceAddress(val name: String, val address: String)

/**
 * Подсказки для текстовых полей-названий из того, что пользователь уже вводил раньше:
 * сервисы (обслуживание, запчасти, прочие расходы), заправки, платные дороги.
 * Чистая функция, тесты `NameSuggestionsTest`.
 */
object NameSuggestions {

    /**
     * @param known Названия из базы, самые недавние первыми (так их отдают DAO)
     * @param input Что уже набрано в поле
     * @return До [limit] подсказок: при пустом поле — последние использованные, иначе
     * совпадения без учёта регистра, сначала начинающиеся с набранного. Точное совпадение
     * с набранным не предлагается — выбирать в нём нечего.
     */
    fun suggest(known: List<String>, input: String, limit: Int = 5): List<String> {
        val query = input.trim()
        val unique = known
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .filterNot { it.equals(query, ignoreCase = true) }

        if (query.isEmpty()) return unique.take(limit)

        val (prefix, other) = unique
            .filter { it.contains(query, ignoreCase = true) }
            .partition { it.startsWith(query, ignoreCase = true) }
        return (prefix + other).take(limit)
    }

    /**
     * Адрес сервиса, выбранного из подсказок: из самой недавней записи с тем же названием
     * (без учёта регистра и пробелов по краям). Регистр сравнивается здесь, а не в SQL:
     * `LOWER()` в SQLite кириллицу не понижает.
     *
     * @param history Пары «сервис — адрес», самые недавние первыми (так их отдаёт DAO)
     * @return null — название пустое или адреса у этого сервиса ни разу не вводили
     */
    fun addressFor(name: String, history: List<ServiceAddress>): String? {
        val wanted = name.trim()
        if (wanted.isEmpty()) return null
        return history
            .firstOrNull { it.name.trim().equals(wanted, ignoreCase = true) && it.address.isNotBlank() }
            ?.address
            ?.trim()
    }
}
