package ar.edu.fguerrero.da1.agendasalud.data.respaldo

import ar.edu.fguerrero.da1.agendasalud.data.local.entity.BloqueoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CambioTurnoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CargoSesionEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CobroEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.PacienteEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.TurnoEntity
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Contenido del respaldo antes de cifrarlo. Es un formato propio y no las entidades de Room,
 * para que un cambio en la base no rompa los respaldos viejos. Las fechas van como texto ISO
 * ("2026-11-03T10:00") y los enums por nombre, así el JSON es legible y estable.
 */
@Serializable
data class RespaldoJson(
    val version: Int = VERSION_FORMATO,
    val creado: String,
    val pacientes: List<PacienteJson>,
    val turnos: List<TurnoJson>,
    val bloqueos: List<BloqueoJson>,
    val cargos: List<CargoJson>,
    val cobros: List<CobroJson>,
    val cambios: List<CambioJson>,
) {
    companion object {
        const val VERSION_FORMATO = 1
    }
}

@Serializable
data class PacienteJson(val id: Long, val nombre: String, val telefono: String? = null, val activo: Boolean)

@Serializable
data class TurnoJson(
    val id: Long,
    val pacienteId: Long,
    val inicio: String,
    val fin: String,
    val duracionMinutos: Int,
    val importeCentavos: Long,
    val estado: String,
    val fechaOriginal: String? = null,
    val motivoCancelacion: String? = null,
)

@Serializable
data class BloqueoJson(val id: Long, val inicio: String, val fin: String, val motivo: String)

@Serializable
data class CargoJson(val id: Long, val turnoId: Long, val importeCentavos: Long)

@Serializable
data class CobroJson(val id: Long, val cargoId: Long, val importeCentavos: Long, val fecha: String, val medio: String)

@Serializable
data class CambioJson(val id: Long, val turnoId: Long, val fechaCambio: String, val inicioAnterior: String, val inicioNuevo: String)

// Entidad → JSON

fun PacienteEntity.aJson() = PacienteJson(id, nombre, telefono, activo)

fun TurnoEntity.aJson() = TurnoJson(
    id, pacienteId, inicio.toString(), fin.toString(), duracionMinutos, importeCentavos,
    estado.name, fechaOriginal?.toString(), motivoCancelacion,
)

fun BloqueoEntity.aJson() = BloqueoJson(id, inicio.toString(), fin.toString(), motivo)
fun CargoSesionEntity.aJson() = CargoJson(id, turnoId, importeCentavos)
fun CobroEntity.aJson() = CobroJson(id, cargoId, importeCentavos, fecha.toString(), medio.name)
fun CambioTurnoEntity.aJson() = CambioJson(id, turnoId, fechaCambio.toString(), inicioAnterior.toString(), inicioNuevo.toString())

// JSON → entidad. Si un valor no se puede leer, lanza una excepción y el respaldo se rechaza entero.

fun PacienteJson.aEntidad() = PacienteEntity(id, nombre, telefono, activo)

fun TurnoJson.aEntidad() = TurnoEntity(
    id, pacienteId, LocalDateTime.parse(inicio), LocalDateTime.parse(fin), duracionMinutos, importeCentavos,
    EstadoTurno.valueOf(estado), fechaOriginal?.let(LocalDateTime::parse), motivoCancelacion,
)

fun BloqueoJson.aEntidad() = BloqueoEntity(id, LocalDateTime.parse(inicio), LocalDateTime.parse(fin), motivo)
fun CargoJson.aEntidad() = CargoSesionEntity(id, turnoId, importeCentavos)
fun CobroJson.aEntidad() = CobroEntity(id, cargoId, importeCentavos, LocalDate.parse(fecha), MedioCobro.valueOf(medio))

fun CambioJson.aEntidad() = CambioTurnoEntity(
    id, turnoId, LocalDateTime.parse(fechaCambio), LocalDateTime.parse(inicioAnterior), LocalDateTime.parse(inicioNuevo),
)
