# ProGuard/R8-правила приложения.
#
# Room, Hilt, OkHttp, Coil, Vico и WorkManager несут собственные consumer-rules
# внутри своих артефактов — для них ничего добавлять не нужно.

# --- Читаемые стектрейсы в release-сборке ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Gson (Room TypeConverters в Converters.kt) ---
# Gson восстанавливает List<String>/List<Long> через рефлексию по generic-сигнатуре
# TypeToken. Без этих правил R8 срезает Signature, и чтение photosPaths /
# installedPartIds / linkedConsumableIds падает в рантайме.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn sun.misc.Unsafe

# WorkItem — единственная собственная модель, уходящая в JSON (Breakdown.workItems).
# Gson пишет и читает её по именам полей: без keep R8 переименует name/cost/notes,
# и работы, сохранённые прежней сборкой, перестанут читаться после обновления.
-keep class com.carlog.domain.model.WorkItem { *; }
