package ar.edu.fguerrero.da1.agendasalud.mock

import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Fechas fijas y en el futuro, para que los tests no dependan del día en que se corren. */
object DatosPrueba {
    /** Un lunes (horario de atención 9 a 18). */
    val LUNES: LocalDate = LocalDate.of(2030, 6, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    val SABADO: LocalDate = LUNES.with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
    val DOMINGO: LocalDate = LUNES.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))

    fun lunesA(hora: Int, minuto: Int = 0): LocalDateTime = LUNES.atTime(hora, minuto)

    const val IMPORTE_SESION = 20_000_00L

    fun turno(
        pacienteId: Long,
        inicio: LocalDateTime = lunesA(10),
        duracionMinutos: Int = 60,
        importeCentavos: Long = IMPORTE_SESION,
    ) = Turno(pacienteId = pacienteId, inicio = inicio, duracionMinutos = duracionMinutos, importeCentavos = importeCentavos)
}
