package com.carlog.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Редирект OAuth принимается только с меткой `state` начатого в приложении входа.
 * Регрессия: любой `carlog://oauth#access_token=…` подключал приложение к чужому Диску.
 */
class YandexOAuthStateTest {

    @Test
    fun `токен с совпавшей меткой принимается`() {
        assertEquals(
            "y0_TOKEN",
            YandexAuthManager.extractToken("access_token=y0_TOKEN&token_type=bearer&state=abc123", "abc123")
        )
    }

    @Test
    fun `токен без метки отклоняется`() {
        assertNull(YandexAuthManager.extractToken("access_token=ATTACKER&token_type=bearer", "abc123"))
    }

    @Test
    fun `токен с чужой меткой отклоняется`() {
        assertNull(YandexAuthManager.extractToken("access_token=ATTACKER&state=evil", "abc123"))
    }

    @Test
    fun `редирект без начатого входа отклоняется`() {
        assertNull(YandexAuthManager.extractToken("access_token=ATTACKER&state=abc123", null))
    }

    @Test
    fun `ошибка авторизации токена не даёт`() {
        assertNull(YandexAuthManager.extractToken("error=access_denied&state=abc123", "abc123"))
    }
}
