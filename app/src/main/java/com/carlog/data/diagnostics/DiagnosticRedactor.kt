package com.carlog.data.diagnostics

/**
 * Вычищает секреты из текста, попадающего в журнал.
 *
 * Зачем это обязательно: журнал задуман так, что пользователь **отправляет файл разработчику**
 * — почтой или через облако. Если в него попадёт OAuth-токен Яндекса или подписанная ссылка
 * загрузки, письмо станет ключом к чужому Яндекс.Диску, и любой, кто увидит его по дороге
 * (пересланное письмо, общая папка, скриншот), получит доступ к данным человека.
 * Поэтому чистим на входе в журнал, а не при экспорте: нет тайминга, при котором
 * секрет успел бы полежать в файле незачищенным.
 *
 * Чистая функция без Android — тестируется на JVM.
 */
object DiagnosticRedactor {

    private const val MASK = "***"

    /** `Authorization: OAuth <token>` */
    private val OAUTH_HEADER = Regex("""OAuth\s+\S+""", RegexOption.IGNORE_CASE)

    /** `#access_token=...` из OAuth-редиректа и `?access_token=...` из запросов */
    private val ACCESS_TOKEN = Regex("""access_token=[^&\s#]*""", RegexOption.IGNORE_CASE)

    /**
     * Query-строка любого URL. Ссылка на загрузку от Яндекса выглядит как
     * `https://uploader1g.disk.yandex.net/upload-target/...?hash=...&sign=...`
     * — сам по себе этот адрес даёт право писать в чужой Диск. Хост оставляем:
     * ради него журнал и заводился.
     */
    private val URL_QUERY = Regex("""(https?://[^\s?#]+)[?#]\S*""", RegexOption.IGNORE_CASE)

    fun redact(text: String?): String? {
        if (text.isNullOrEmpty()) return text
        return text
            .replace(OAUTH_HEADER, "OAuth $MASK")
            .replace(ACCESS_TOKEN, "access_token=$MASK")
            .replace(URL_QUERY) { match -> "${match.groupValues[1]}?$MASK" }
    }
}
