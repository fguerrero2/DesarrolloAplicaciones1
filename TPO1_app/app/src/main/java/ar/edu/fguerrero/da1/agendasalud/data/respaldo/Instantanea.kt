package ar.edu.fguerrero.da1.agendasalud.data.respaldo

import androidx.room.withTransaction
import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.domain.model.ResumenRespaldo
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.LocalDateTime

/**
 * Copia completa de los datos ("instantánea") en el formato de [RespaldoJson]. La usan el respaldo
 * en archivo y la sincronización con el backend: un solo formato para las dos cosas.
 */
class Instantanea(private val db: AgendaDatabase) {
    private val dao = db.respaldoDao()

    /** Se lee en una transacción para que la copia sea consistente (nada cambia a la mitad). */
    suspend fun leer(): RespaldoJson = db.withTransaction {
        RespaldoJson(
            creado = LocalDateTime.now().toString(),
            pacientes = dao.pacientes().map { it.aJson() },
            turnos = dao.turnos().map { it.aJson() },
            bloqueos = dao.bloqueos().map { it.aJson() },
            cargos = dao.cargos().map { it.aJson() },
            cobros = dao.cobros().map { it.aJson() },
            cambios = dao.cambios().map { it.aJson() },
        )
    }

    /**
     * Reemplaza TODOS los datos por los de [copia], todo o nada: si algo falla (por ejemplo, una
     * clave foránea), la base queda como estaba. Lanza excepción si un valor no se puede leer.
     */
    suspend fun restaurar(copia: RespaldoJson): ResumenRespaldo {
        require(copia.version <= RespaldoJson.VERSION_FORMATO) { "Formato más nuevo que esta versión de la app" }
        // Se convierte todo ANTES de borrar nada: una fecha ilegible no deja la base vacía
        val pacientes = copia.pacientes.map { it.aEntidad() }
        val turnos = copia.turnos.map { it.aEntidad() }
        val cambios = copia.cambios.map { it.aEntidad() }
        val bloqueos = copia.bloqueos.map { it.aEntidad() }
        val cargos = copia.cargos.map { it.aEntidad() }
        val cobros = copia.cobros.map { it.aEntidad() }
        db.withTransaction {
            dao.borrarTodo()
            dao.insertarPacientes(pacientes)
            dao.insertarTurnos(turnos)
            dao.insertarCambios(cambios)
            dao.insertarBloqueos(bloqueos)
            dao.insertarCargos(cargos)
            dao.insertarCobros(cobros)
        }
        return copia.resumen()
    }

    companion object {
        val formato = Json { ignoreUnknownKeys = true }

        /**
         * SHA-256 del contenido (sin la fecha de creación). Si cambia respecto de la última
         * sincronización, hay cambios locales sin subir.
         */
        fun huella(copia: RespaldoJson): String = MessageDigest.getInstance("SHA-256")
            .digest(formato.encodeToString(copia.copy(creado = "")).encodeToByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

fun RespaldoJson.resumen() = ResumenRespaldo(pacientes = pacientes.size, turnos = turnos.size, cobros = cobros.size)
