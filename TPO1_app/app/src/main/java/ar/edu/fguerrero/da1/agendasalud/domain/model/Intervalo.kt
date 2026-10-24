package ar.edu.fguerrero.da1.agendasalud.domain.model

import java.time.LocalDateTime

/** Intervalo semiabierto [inicio, fin): un turno que termina 10:00 no choca con otro que empieza 10:00. */
data class Intervalo(
    val inicio: LocalDateTime,
    val fin: LocalDateTime,
) {
    val esValido: Boolean get() = fin.isAfter(inicio)

    fun seSuperponeCon(otro: Intervalo): Boolean =
        inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin)
}
