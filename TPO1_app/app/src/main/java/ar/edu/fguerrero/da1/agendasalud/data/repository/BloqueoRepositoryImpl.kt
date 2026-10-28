package ar.edu.fguerrero.da1.agendasalud.data.repository

import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.local.aDominio
import ar.edu.fguerrero.da1.agendasalud.data.local.aEntidad
import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.repository.BloqueoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.rules.ReglasAgenda
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

class BloqueoRepositoryImpl(private val db: AgendaDatabase) : BloqueoRepository {
    private val dao = db.bloqueoDao()
    private val turnoDao = db.turnoDao()

    override fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<BloqueoAgenda>> =
        dao.observarEntre(desde, hasta).map { lista -> lista.map { it.aDominio() } }

    override suspend fun crear(bloqueo: BloqueoAgenda): Resultado<Long> = db.transaccion {
        val turnos = turnoDao.activosEntre(bloqueo.inicio, bloqueo.fin).map { it.aDominio() }
        ReglasAgenda.validarBloqueo(bloqueo.intervalo, turnos)?.let { fallar(it) }
        dao.insertar(bloqueo.copy(id = 0).aEntidad())
    }

    override suspend fun eliminar(id: Long) = dao.eliminar(id)
}
