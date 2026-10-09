package com.carlog.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CarFormRulesTest {

    @Test
    fun `у новой машины объём бака обязателен`() {
        assertEquals("Обязательное поле", CarFormRules.volumeError("", required = true))
    }

    @Test
    fun `у старой машины пустой объём не мешает сохранить правку`() {
        assertNull(CarFormRules.volumeError("", required = false))
    }

    @Test
    fun `запятая из русской раскладки принимается`() {
        assertNull(CarFormRules.volumeError("42,5", required = true))
    }

    @Test
    fun `ноль, отрицательное и мусор отклоняются даже в необязательном поле`() {
        assertNotNull(CarFormRules.volumeError("0", required = false))
        assertNotNull(CarFormRules.volumeError("-5", required = false))
        assertNotNull(CarFormRules.volumeError("много", required = false))
    }
}
