package com.carlog.presentation.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Окно «Что нового» должно появляться ровно один раз на версию */
class WhatsNewVisibilityTest {

    private val current = "1.2.1"

    @Test
    fun `после обновления с прошлой версии показываем`() {
        assertTrue(shouldShowWhatsNew(isFirstLaunch = false, shownForVersion = "1.2.0", currentVersion = current))
    }

    @Test
    fun `на свежей установке после онбординга показываем`() {
        assertTrue(shouldShowWhatsNew(isFirstLaunch = false, shownForVersion = "", currentVersion = current))
    }

    @Test
    fun `для текущей версии уже показывали — больше не показываем`() {
        assertFalse(shouldShowWhatsNew(isFirstLaunch = false, shownForVersion = current, currentVersion = current))
    }

    @Test
    fun `во время онбординга не показываем`() {
        assertFalse(shouldShowWhatsNew(isFirstLaunch = true, shownForVersion = "", currentVersion = current))
    }

    @Test
    fun `пока настройки не прочитаны — не показываем`() {
        assertFalse(shouldShowWhatsNew(isFirstLaunch = null, shownForVersion = null, currentVersion = current))
        assertFalse(shouldShowWhatsNew(isFirstLaunch = false, shownForVersion = null, currentVersion = current))
    }
}
