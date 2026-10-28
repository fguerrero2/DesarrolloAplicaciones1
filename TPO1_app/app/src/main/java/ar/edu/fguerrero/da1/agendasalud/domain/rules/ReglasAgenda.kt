package ar.edu.fguerrero.da1.agendasalud.domain.rules

import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.Intervalo
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno

/** Reglas de superposición de agenda (RF02, RF03). Kotlin puro, sin Android. */
object ReglasAgenda {

    /**
     * Valida que [intervalo] pueda ocuparse. Los turnos cancelados liberan su horario y
     * [excluirTurnoId] permite ignorar el propio turno al reprogramar.
     */
    fun validarHorario(
        intervalo: Intervalo,
        turnos: List<Turno>,
        bloqueos: List<BloqueoAgenda>,
        excluirTurnoId: Long? = null,
    ): ErrorDominio? = when {
        !intervalo.esValido -> ErrorDominio.IntervaloInvalido
        !HorarioAtencion.contiene(intervalo) -> ErrorDominio.FueraDeHorario
        bloqueos.any { it.intervalo.seSuperponeCon(intervalo) } -> ErrorDominio.PeriodoBloqueado
        turnos.any {
            it.id != excluirTurnoId &&
                it.estado != EstadoTurno.CANCELADO &&
                it.intervalo.seSuperponeCon(intervalo)
        } -> ErrorDominio.HorarioOcupado
        else -> null
    }

    /** Un bloqueo no puede pisar turnos programados: primero hay que reprogramarlos o cancelarlos. */
    fun validarBloqueo(intervalo: Intervalo, turnos: List<Turno>): ErrorDominio? = when {
        !intervalo.esValido -> ErrorDominio.IntervaloInvalido
        turnos.any { it.estado == EstadoTurno.PROGRAMADO && it.intervalo.seSuperponeCon(intervalo) } ->
            ErrorDominio.TurnosEnPeriodo
        else -> null
    }
}
