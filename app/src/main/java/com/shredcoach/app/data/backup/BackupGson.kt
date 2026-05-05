package com.shredcoach.app.data.backup

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Provider Gson configuré pour le backup ShredCoach.
 *
 * Pourquoi un Gson dédié : le Gson par défaut ne sait pas (de manière propre)
 * sérialiser `java.time.LocalDate / LocalDateTime / LocalTime` — il tomberait
 * sur la réflexion, produisant du JSON verbeux et fragile au refactoring.
 *
 * On enregistre des [TypeAdapter] qui produisent les mêmes strings ISO que
 * les Room TypeConverters (cf. `data.local.converter.Converters`) — round-trip
 * garanti entre la DB et le backup, lecture humaine si l'utilisateur ouvre
 * le JSON dans un éditeur.
 *
 * `serializeNulls = true` : on garde les champs nuls explicitement (vs. omis).
 * Important pour les nullable Kotlin avec valeur par défaut non-null : sans
 * ce flag, un champ explicitement mis à null serait omis et reprendrait sa
 * valeur par défaut au restore — perte d'info.
 *
 * `setPrettyPrinting()` : volontairement omis. Le JSON est pour la machine ;
 * pretty printing gonfle la taille de ~30% pour aucun gain (l'archive ZIP
 * derrière compresse de toute façon).
 */
object BackupGson {

    val instance = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(LocalDate::class.java, LocalDateAdapter)
        .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter)
        .registerTypeAdapter(LocalTime::class.java, LocalTimeAdapter)
        .create()

    private object LocalDateAdapter : TypeAdapter<LocalDate>() {
        override fun write(out: JsonWriter, value: LocalDate?) {
            if (value == null) out.nullValue() else out.value(value.toString())
        }

        override fun read(`in`: JsonReader): LocalDate? {
            if (`in`.peek() == JsonToken.NULL) {
                `in`.nextNull()
                return null
            }
            return LocalDate.parse(`in`.nextString())
        }
    }

    private object LocalDateTimeAdapter : TypeAdapter<LocalDateTime>() {
        override fun write(out: JsonWriter, value: LocalDateTime?) {
            if (value == null) out.nullValue() else out.value(value.toString())
        }

        override fun read(`in`: JsonReader): LocalDateTime? {
            if (`in`.peek() == JsonToken.NULL) {
                `in`.nextNull()
                return null
            }
            return LocalDateTime.parse(`in`.nextString())
        }
    }

    private object LocalTimeAdapter : TypeAdapter<LocalTime>() {
        override fun write(out: JsonWriter, value: LocalTime?) {
            if (value == null) out.nullValue() else out.value(value.toString())
        }

        override fun read(`in`: JsonReader): LocalTime? {
            if (`in`.peek() == JsonToken.NULL) {
                `in`.nextNull()
                return null
            }
            return LocalTime.parse(`in`.nextString())
        }
    }
}
