package ar.edu.fguerrero.da1.agendasalud.data.local

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * Las fechas se guardan como hora local del dispositivo ("hora de pared"), codificada en
 * segundos con offset fijo para que sean ordenables y comparables en SQL.
 */
class Converters {
    @TypeConverter
    fun desdeFechaHora(valor: LocalDateTime?): Long? = valor?.toEpochSecond(ZoneOffset.UTC)

    @TypeConverter
    fun aFechaHora(valor: Long?): LocalDateTime? = valor?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    @TypeConverter
    fun desdeFecha(valor: LocalDate?): Long? = valor?.toEpochDay()

    @TypeConverter
    fun aFecha(valor: Long?): LocalDate? = valor?.let(LocalDate::ofEpochDay)
}
