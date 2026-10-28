package ar.edu.fguerrero.da1.agendasalud.data.repository

import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.local.aDominio
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CobroEntity
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoPendiente
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CobroRepository
import ar.edu.fguerrero.da1.agendasalud.domain.rules.ReglasCobro
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class CobroRepositoryImpl(private val db: AgendaDatabase) : CobroRepository {
    private val dao = db.cobroDao()

    override fun observarPendientes(): Flow<List<CargoPendiente>> =
        dao.observarCargos().map { lista ->
            lista.map { it.aDominio() }.filter { it.pendienteCentavos > 0 }.sortedBy { it.fechaSesion }
        }

    override fun observarCargos(): Flow<List<CargoPendiente>> =
        dao.observarCargos().map { lista -> lista.map { it.aDominio() }.sortedByDescending { it.fechaSesion } }

    override fun observarSaldos(): Flow<Map<Long, Long>> =
        dao.observarSaldos().map { filas -> filas.associate { it.pacienteId to it.saldoCentavos } }

    override fun observarSaldoPaciente(pacienteId: Long): Flow<Long> = dao.observarSaldoPaciente(pacienteId)

    override suspend fun obtenerCargoDeTurno(turnoId: Long): CargoPendiente? =
        dao.cargoConCobradoDeTurno(turnoId)?.aDominio()

    override suspend fun registrar(
        cargoId: Long,
        montoCentavos: Long,
        fecha: LocalDate,
        medio: MedioCobro,
    ): Resultado<Long> = db.transaccion {
        val cargo = dao.cargo(cargoId) ?: fallar(ErrorDominio.NoEncontrado)
        val pendiente = ReglasCobro.pendiente(cargo.importeCentavos, dao.cobrosDeCargo(cargoId))
        ReglasCobro.validarCobro(montoCentavos, pendiente)?.let { fallar(it) }
        dao.insertarCobro(CobroEntity(cargoId = cargoId, importeCentavos = montoCentavos, fecha = fecha, medio = medio))
    }
}
