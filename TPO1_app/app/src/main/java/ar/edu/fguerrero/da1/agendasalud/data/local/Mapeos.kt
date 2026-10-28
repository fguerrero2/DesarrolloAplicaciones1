package ar.edu.fguerrero.da1.agendasalud.data.local

import ar.edu.fguerrero.da1.agendasalud.data.local.entity.BloqueoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CargoConCobrado
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CargoSesionEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.PacienteEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.TurnoConNombre
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.TurnoEntity
import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoPendiente
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoSesion
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.model.TurnoConPaciente

fun PacienteEntity.aDominio() = Paciente(id, nombre, telefono, activo)
fun Paciente.aEntidad() = PacienteEntity(id, nombre.trim(), telefono?.trim()?.ifEmpty { null }, activo)

fun TurnoEntity.aDominio() = Turno(
    id = id,
    pacienteId = pacienteId,
    inicio = inicio,
    duracionMinutos = duracionMinutos,
    importeCentavos = importeCentavos,
    estado = estado,
    fechaOriginal = fechaOriginal,
    motivoCancelacion = motivoCancelacion,
)

fun Turno.aEntidad() = TurnoEntity(
    id = id,
    pacienteId = pacienteId,
    inicio = inicio,
    fin = fin,
    duracionMinutos = duracionMinutos,
    importeCentavos = importeCentavos,
    estado = estado,
    fechaOriginal = fechaOriginal,
    motivoCancelacion = motivoCancelacion,
)

fun TurnoConNombre.aDominio() = TurnoConPaciente(turno.aDominio(), nombrePaciente)

fun BloqueoEntity.aDominio() = BloqueoAgenda(id, inicio, fin, motivo)
fun BloqueoAgenda.aEntidad() = BloqueoEntity(id, inicio, fin, motivo.trim())

fun CargoSesionEntity.aDominio() = CargoSesion(id, turnoId, importeCentavos)

fun CargoConCobrado.aDominio() = CargoPendiente(
    cargoId = cargoId,
    turnoId = turnoId,
    pacienteId = pacienteId,
    nombrePaciente = nombrePaciente,
    fechaSesion = fechaSesion,
    importeCentavos = importeCentavos,
    cobradoCentavos = cobradoCentavos,
)
