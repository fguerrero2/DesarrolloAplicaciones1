package ar.edu.fguerrero.da1.agendasalud.domain.usecase

import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PacienteRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.TurnoRepository

class ReservarTurno(
    private val pacientes: PacienteRepository,
    private val turnos: TurnoRepository,
) {
    suspend operator fun invoke(turno: Turno): Resultado<Long> {
        val paciente = pacientes.obtener(turno.pacienteId) ?: return Resultado.Fallo(ErrorDominio.NoEncontrado)
        if (!paciente.activo) return Resultado.Fallo(ErrorDominio.PacienteInactivo)
        if (turno.duracionMinutos <= 0) return Resultado.Fallo(ErrorDominio.IntervaloInvalido)
        if (turno.importeCentavos < 0) return Resultado.Fallo(ErrorDominio.MontoNoPositivo)
        return turnos.reservar(turno)
    }
}
