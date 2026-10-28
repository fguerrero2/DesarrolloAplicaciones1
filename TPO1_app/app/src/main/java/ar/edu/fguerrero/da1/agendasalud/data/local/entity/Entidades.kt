package ar.edu.fguerrero.da1.agendasalud.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "pacientes", indices = [Index("nombre")])
data class PacienteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val telefono: String?,
    val activo: Boolean,
)

@Entity(
    tableName = "turnos",
    foreignKeys = [ForeignKey(PacienteEntity::class, ["id"], ["pacienteId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("pacienteId"), Index("inicio")],
)
data class TurnoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pacienteId: Long,
    val inicio: LocalDateTime,
    // Redundante con duración, pero permite consultar superposición en SQL
    val fin: LocalDateTime,
    val duracionMinutos: Int,
    val importeCentavos: Long,
    val estado: EstadoTurno,
    val fechaOriginal: LocalDateTime?,
    val motivoCancelacion: String?,
)

@Entity(tableName = "bloqueos", indices = [Index("inicio")])
data class BloqueoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inicio: LocalDateTime,
    val fin: LocalDateTime,
    val motivo: String,
)

@Entity(
    tableName = "cargos_sesion",
    foreignKeys = [ForeignKey(TurnoEntity::class, ["id"], ["turnoId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("turnoId", unique = true)],
)
data class CargoSesionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnoId: Long,
    val importeCentavos: Long,
)

@Entity(
    tableName = "cobros",
    foreignKeys = [ForeignKey(CargoSesionEntity::class, ["id"], ["cargoId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("cargoId")],
)
data class CobroEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cargoId: Long,
    val importeCentavos: Long,
    val fecha: LocalDate,
    val medio: MedioCobro,
)

@Entity(
    tableName = "cambios_turno",
    foreignKeys = [ForeignKey(TurnoEntity::class, ["id"], ["turnoId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("turnoId")],
)
data class CambioTurnoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val turnoId: Long,
    val fechaCambio: LocalDateTime,
    val inicioAnterior: LocalDateTime,
    val inicioNuevo: LocalDateTime,
)

/** Fila de consulta: turno con el nombre de su paciente. */
data class TurnoConNombre(
    @Embedded val turno: TurnoEntity,
    val nombrePaciente: String,
)

/** Fila de consulta: cargo con lo cobrado hasta el momento. */
data class CargoConCobrado(
    val cargoId: Long,
    val turnoId: Long,
    val pacienteId: Long,
    val nombrePaciente: String,
    val fechaSesion: LocalDateTime,
    val importeCentavos: Long,
    val cobradoCentavos: Long,
)

/** Fila de consulta: saldo pendiente acumulado de un paciente. */
data class SaldoPacienteFila(
    val pacienteId: Long,
    val saldoCentavos: Long,
)
