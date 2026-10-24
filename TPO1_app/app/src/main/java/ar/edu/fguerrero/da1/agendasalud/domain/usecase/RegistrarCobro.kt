package ar.edu.fguerrero.da1.agendasalud.domain.usecase

import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CobroRepository
import java.time.LocalDate

class RegistrarCobro(private val cobros: CobroRepository) {
    suspend operator fun invoke(
        cargoId: Long,
        montoCentavos: Long,
        medio: MedioCobro,
        fecha: LocalDate = LocalDate.now(),
    ): Resultado<Long> {
        // Validación temprana para feedback inmediato; el repositorio vuelve a validar dentro de la transacción.
        if (montoCentavos <= 0) return Resultado.Fallo(ErrorDominio.MontoNoPositivo)
        return cobros.registrar(cargoId, montoCentavos, fecha, medio)
    }
}
