package ar.edu.fguerrero.da1.agendasalud.domain.model

import java.time.LocalDateTime

/** Registro administrativo de una reprogramación. */
data class CambioTurno(
    val id: Long = 0,
    val turnoId: Long,
    val fechaCambio: LocalDateTime,
    val inicioAnterior: LocalDateTime,
    val inicioNuevo: LocalDateTime,
)
