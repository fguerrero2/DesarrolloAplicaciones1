package ar.edu.fguerrero.da1.agendasalud.domain.rules

import ar.edu.fguerrero.da1.agendasalud.domain.model.Intervalo
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Horario de atención: lunes a viernes 9 a 18, sábados 9 a 12, domingos cerrado. */
object HorarioAtencion {

    fun franja(fecha: LocalDate): Pair<LocalTime, LocalTime>? = when (fecha.dayOfWeek) {
        DayOfWeek.SUNDAY -> null
        DayOfWeek.SATURDAY -> LocalTime.of(9, 0) to LocalTime.of(12, 0)
        else -> LocalTime.of(9, 0) to LocalTime.of(18, 0)
    }

    fun contiene(intervalo: Intervalo): Boolean {
        val dia = intervalo.inicio.toLocalDate()
        val (apertura, cierre) = franja(dia) ?: return false
        return !intervalo.inicio.isBefore(dia.atTime(apertura)) && !intervalo.fin.isAfter(dia.atTime(cierre))
    }
}
