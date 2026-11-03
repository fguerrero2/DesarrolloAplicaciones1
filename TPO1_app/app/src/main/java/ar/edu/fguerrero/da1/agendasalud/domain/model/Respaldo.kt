package ar.edu.fguerrero.da1.agendasalud.domain.model

/** Cuánto se guardó o se recuperó, para confirmárselo al usuario. */
data class ResumenRespaldo(val pacientes: Int, val turnos: Int, val cobros: Int)
