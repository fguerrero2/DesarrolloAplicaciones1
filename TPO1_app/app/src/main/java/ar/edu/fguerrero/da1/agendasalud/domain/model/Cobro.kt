package ar.edu.fguerrero.da1.agendasalud.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class MedioCobro { EFECTIVO, MERCADO_PAGO }

/** Cargo generado al completar una sesión; único por turno. */
data class CargoSesion(
    val id: Long = 0,
    val turnoId: Long,
    val importeCentavos: Long,
)

/** Pago total o parcial aplicado a un cargo. */
data class Cobro(
    val id: Long = 0,
    val cargoId: Long,
    val importeCentavos: Long,
    val fecha: LocalDate,
    val medio: MedioCobro,
)

/** Cargo con saldo pendiente, para la pantalla de cobros pendientes. */
data class CargoPendiente(
    val cargoId: Long,
    val turnoId: Long,
    val pacienteId: Long,
    val nombrePaciente: String,
    val fechaSesion: LocalDateTime,
    val importeCentavos: Long,
    val cobradoCentavos: Long,
) {
    val pendienteCentavos: Long get() = importeCentavos - cobradoCentavos
}
