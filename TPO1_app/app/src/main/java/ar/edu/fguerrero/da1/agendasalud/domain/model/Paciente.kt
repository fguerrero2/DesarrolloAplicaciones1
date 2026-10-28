package ar.edu.fguerrero.da1.agendasalud.domain.model

data class Paciente(
    val id: Long = 0,
    val nombre: String,
    val telefono: String? = null,
    val activo: Boolean = true,
)
