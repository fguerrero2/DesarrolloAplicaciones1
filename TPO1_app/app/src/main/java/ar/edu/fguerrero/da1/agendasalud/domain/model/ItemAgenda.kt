package ar.edu.fguerrero.da1.agendasalud.domain.model

import java.time.LocalDateTime

/** Fila de la agenda de un día: turno, hueco libre o período bloqueado. */
sealed interface ItemAgenda {
    val inicio: LocalDateTime

    data class Ocupado(val turno: TurnoConPaciente) : ItemAgenda {
        override val inicio: LocalDateTime get() = turno.turno.inicio
    }

    data class Libre(override val inicio: LocalDateTime, val fin: LocalDateTime) : ItemAgenda

    data class Bloqueado(val bloqueo: BloqueoAgenda) : ItemAgenda {
        override val inicio: LocalDateTime get() = bloqueo.inicio
    }
}
