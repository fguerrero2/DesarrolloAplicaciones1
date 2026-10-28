package ar.edu.fguerrero.da1.agendasalud.data.repository

import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.local.aDominio
import ar.edu.fguerrero.da1.agendasalud.data.local.aEntidad
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PacienteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PacienteRepositoryImpl(private val db: AgendaDatabase) : PacienteRepository {
    private val dao = db.pacienteDao()

    override fun observar(busqueda: String, incluirInactivos: Boolean): Flow<List<Paciente>> =
        dao.observar(busqueda.trim(), incluirInactivos).map { lista -> lista.map { it.aDominio() } }

    override suspend fun obtener(id: Long): Paciente? = dao.obtener(id)?.aDominio()

    override suspend fun guardar(paciente: Paciente): Resultado<Long> {
        if (paciente.nombre.isBlank()) return Resultado.Fallo(ErrorDominio.NombreInvalido)
        return db.transaccion {
            if (paciente.id == 0L) {
                dao.insertar(paciente.aEntidad())
            } else {
                if (dao.actualizar(paciente.aEntidad()) == 0) fallar(ErrorDominio.NoEncontrado)
                paciente.id
            }
        }
    }

    override suspend fun desactivar(id: Long): Resultado<Unit> = db.transaccion {
        if (dao.desactivar(id) == 0) fallar(ErrorDominio.NoEncontrado)
    }
}
