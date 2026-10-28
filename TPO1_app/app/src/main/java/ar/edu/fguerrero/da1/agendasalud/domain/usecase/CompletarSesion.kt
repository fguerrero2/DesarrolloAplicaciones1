package ar.edu.fguerrero.da1.agendasalud.domain.usecase

import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoSesion
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.repository.TurnoRepository

class CompletarSesion(private val turnos: TurnoRepository) {
    suspend operator fun invoke(turnoId: Long): Resultado<CargoSesion> = turnos.completar(turnoId)
}
