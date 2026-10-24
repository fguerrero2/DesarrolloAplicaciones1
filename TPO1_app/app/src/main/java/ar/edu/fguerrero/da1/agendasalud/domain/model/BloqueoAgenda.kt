package ar.edu.fguerrero.da1.agendasalud.domain.model

import java.time.LocalDateTime

/** Período sin atención (vacaciones, cursos, eventos). */
data class BloqueoAgenda(
    val id: Long = 0,
    val inicio: LocalDateTime,
    val fin: LocalDateTime,
    val motivo: String,
) {
    val intervalo: Intervalo get() = Intervalo(inicio, fin)
}
