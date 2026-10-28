package ar.edu.fguerrero.da1.agendasalud.data.repository

import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.local.aDominio
import ar.edu.fguerrero.da1.agendasalud.data.local.aEntidad
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CambioTurnoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CargoSesionEntity
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoSesion
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.Intervalo
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.model.TurnoConPaciente
import ar.edu.fguerrero.da1.agendasalud.domain.repository.TurnoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.rules.ReglasAgenda
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

class TurnoRepositoryImpl(private val db: AgendaDatabase) : TurnoRepository {
    private val turnoDao = db.turnoDao()
    private val bloqueoDao = db.bloqueoDao()
    private val cobroDao = db.cobroDao()

    override fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<TurnoConPaciente>> =
        turnoDao.observarEntre(desde, hasta).map { lista -> lista.map { it.aDominio() } }

    override fun observarDePaciente(pacienteId: Long): Flow<List<Turno>> =
        turnoDao.observarDePaciente(pacienteId).map { lista -> lista.map { it.aDominio() } }

    override suspend fun obtener(id: Long): TurnoConPaciente? = turnoDao.obtenerConPaciente(id)?.aDominio()

    override suspend fun reservar(turno: Turno): Resultado<Long> = db.transaccion {
        validarHorario(turno.intervalo, excluirTurnoId = null)
        turnoDao.insertar(turno.copy(id = 0, estado = EstadoTurno.PROGRAMADO).aEntidad())
    }

    override suspend fun reprogramar(turnoId: Long, nuevoInicio: LocalDateTime): Resultado<Unit> = db.transaccion {
        val actual = turnoProgramado(turnoId)
        val nuevo = actual.copy(inicio = nuevoInicio, fechaOriginal = actual.fechaOriginal ?: actual.inicio)
        validarHorario(nuevo.intervalo, excluirTurnoId = turnoId)
        turnoDao.actualizar(nuevo.aEntidad())
        turnoDao.insertarCambio(
            CambioTurnoEntity(
                turnoId = turnoId,
                fechaCambio = LocalDateTime.now(),
                inicioAnterior = actual.inicio,
                inicioNuevo = nuevoInicio,
            )
        )
    }

    override suspend fun cancelar(turnoId: Long, motivo: String?): Resultado<Unit> = db.transaccion {
        val actual = turnoProgramado(turnoId)
        turnoDao.actualizar(
            actual.copy(estado = EstadoTurno.CANCELADO, motivoCancelacion = motivo?.trim()?.ifEmpty { null }).aEntidad()
        )
    }

    override suspend fun completar(turnoId: Long): Resultado<CargoSesion> = db.transaccion {
        val actual = turnoDao.obtener(turnoId)?.aDominio() ?: fallar(ErrorDominio.NoEncontrado)
        // Reintento idempotente: si ya se completó, devolvemos el cargo existente sin duplicarlo
        cobroDao.cargoDeTurno(turnoId)?.let { return@transaccion it.aDominio() }
        if (actual.estado != EstadoTurno.PROGRAMADO) fallar(ErrorDominio.EstadoNoPermitido)
        turnoDao.actualizar(actual.copy(estado = EstadoTurno.COMPLETADO).aEntidad())
        val cargo = CargoSesionEntity(turnoId = turnoId, importeCentavos = actual.importeCentavos)
        cargo.copy(id = cobroDao.insertarCargo(cargo)).aDominio()
    }

    override fun observarCancelacionesDelAnio(pacienteId: Long, anio: Int): Flow<Int> {
        val desde = LocalDate.of(anio, 1, 1).atStartOfDay()
        return turnoDao.observarCanceladosEntre(pacienteId, desde, desde.plusYears(1))
    }

    private suspend fun turnoProgramado(turnoId: Long): Turno {
        val turno = turnoDao.obtener(turnoId)?.aDominio() ?: fallar(ErrorDominio.NoEncontrado)
        if (turno.estado != EstadoTurno.PROGRAMADO) fallar(ErrorDominio.EstadoNoPermitido)
        return turno
    }

    private suspend fun validarHorario(intervalo: Intervalo, excluirTurnoId: Long?) {
        val turnos = turnoDao.activosEntre(intervalo.inicio, intervalo.fin).map { it.aDominio() }
        val bloqueos = bloqueoDao.entre(intervalo.inicio, intervalo.fin).map { it.aDominio() }
        ReglasAgenda.validarHorario(intervalo, turnos, bloqueos, excluirTurnoId)?.let { fallar(it) }
    }
}
