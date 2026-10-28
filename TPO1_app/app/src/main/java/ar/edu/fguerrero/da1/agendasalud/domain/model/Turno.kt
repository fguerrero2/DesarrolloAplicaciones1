package ar.edu.fguerrero.da1.agendasalud.domain.model

import java.time.LocalDateTime

enum class EstadoTurno { PROGRAMADO, COMPLETADO, CANCELADO }

/**
 * Sesión agendada con un paciente. Los importes se manejan en centavos para evitar
 * errores de redondeo. [fechaOriginal] conserva el horario previo a la primera reprogramación.
 */
data class Turno(
    val id: Long = 0,
    val pacienteId: Long,
    val inicio: LocalDateTime,
    val duracionMinutos: Int,
    val importeCentavos: Long,
    val estado: EstadoTurno = EstadoTurno.PROGRAMADO,
    val fechaOriginal: LocalDateTime? = null,
    val motivoCancelacion: String? = null,
) {
    val fin: LocalDateTime get() = inicio.plusMinutes(duracionMinutos.toLong())
    val intervalo: Intervalo get() = Intervalo(inicio, fin)
}

/** Turno junto al nombre del paciente, para listados de agenda. */
data class TurnoConPaciente(
    val turno: Turno,
    val nombrePaciente: String,
)
