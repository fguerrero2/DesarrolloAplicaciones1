package ar.edu.fguerrero.da1.agendasalud.data.repository

import android.content.Context
import androidx.core.net.toUri
import androidx.room.withTransaction
import ar.edu.fguerrero.da1.agendasalud.core.security.CifradoRespaldo
import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.BloqueoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CambioTurnoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CargoSesionEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CobroEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.PacienteEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.TurnoEntity
import ar.edu.fguerrero.da1.agendasalud.data.respaldo.RespaldoJson
import ar.edu.fguerrero.da1.agendasalud.data.respaldo.aEntidad
import ar.edu.fguerrero.da1.agendasalud.data.respaldo.aJson
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.model.ResumenRespaldo
import ar.edu.fguerrero.da1.agendasalud.domain.repository.RespaldoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Respaldo cifrado en un archivo que el usuario elige con el selector de Android (Storage Access
 * Framework): puede guardarlo en Google Drive, mandarlo por mail o copiarlo a un pendrive.
 * La app nunca elige la ruta ni pide permisos de almacenamiento.
 */
class RespaldoRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AgendaDatabase,
) : RespaldoRepository {

    private val dao = db.respaldoDao()
    private val formato = Json { ignoreUnknownKeys = true }

    override suspend fun exportar(destino: String, contrasenia: CharArray): Resultado<ResumenRespaldo> =
        conContrasenia(contrasenia) {
            // Se lee en una transacción para que el respaldo sea una foto consistente
            val respaldo = db.withTransaction {
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
            val cifrado = CifradoRespaldo.cifrar(formato.encodeToString(respaldo).encodeToByteArray(), contrasenia)
            val salida = context.contentResolver.openOutputStream(destino.toUri(), "w")
                ?: return@conContrasenia Resultado.Fallo(ErrorDominio.ErrorArchivo)
            salida.use { it.write(cifrado) }
            Resultado.Exito(respaldo.resumen())
        }

    override suspend fun restaurar(origen: String, contrasenia: CharArray): Resultado<ResumenRespaldo> =
        conContrasenia(contrasenia) {
            val archivo = context.contentResolver.openInputStream(origen.toUri())?.use(::leerConLimite)
                ?: return@conContrasenia Resultado.Fallo(ErrorDominio.ErrorArchivo)

            // Contraseña incorrecta, archivo dañado o de otra app: se rechaza sin tocar la base
            val respaldo = try {
                val json = CifradoRespaldo.descifrar(archivo, contrasenia).decodeToString()
                formato.decodeFromString<RespaldoJson>(json).also {
                    if (it.version > RespaldoJson.VERSION_FORMATO) return@conContrasenia Resultado.Fallo(ErrorDominio.RespaldoInvalido)
                }
            } catch (e: CifradoRespaldo.RespaldoIlegibleException) {
                return@conContrasenia Resultado.Fallo(ErrorDominio.RespaldoInvalido)
            } catch (e: IllegalArgumentException) {
                // SerializationException hereda de IllegalArgumentException
                return@conContrasenia Resultado.Fallo(ErrorDominio.RespaldoInvalido)
            }

            val filas = try {
                Filas(
                    respaldo.pacientes.map { it.aEntidad() },
                    respaldo.turnos.map { it.aEntidad() },
                    respaldo.bloqueos.map { it.aEntidad() },
                    respaldo.cargos.map { it.aEntidad() },
                    respaldo.cobros.map { it.aEntidad() },
                    respaldo.cambios.map { it.aEntidad() },
                )
            } catch (e: RuntimeException) {
                // Fecha o estado ilegible dentro de un JSON válido
                return@conContrasenia Resultado.Fallo(ErrorDominio.RespaldoInvalido)
            }

            // Todo o nada: si falla un insert (por ejemplo, una clave foránea), la base queda como estaba
            db.transaccion {
                dao.borrarTodo()
                dao.insertarPacientes(filas.pacientes)
                dao.insertarTurnos(filas.turnos)
                dao.insertarCambios(filas.cambios)
                dao.insertarBloqueos(filas.bloqueos)
                dao.insertarCargos(filas.cargos)
                dao.insertarCobros(filas.cobros)
                respaldo.resumen()
            }
        }

    /**
     * Corre [bloque] fuera del hilo principal (PBKDF2 y el archivo son lentos) y borra la
     * contraseña de memoria al terminar, salió bien o mal.
     */
    private suspend fun conContrasenia(
        contrasenia: CharArray,
        bloque: suspend () -> Resultado<ResumenRespaldo>,
    ): Resultado<ResumenRespaldo> = withContext(Dispatchers.IO) {
        try {
            bloque()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Resultado.Fallo(ErrorDominio.ErrorArchivo)
        } finally {
            contrasenia.fill('\u0000')
        }
    }

    /**
     * Un respaldo real pesa pocos KB: si el archivo elegido es enorme no puede ser nuestro y se corta
     * antes de llenar la memoria. (InputStream.readNBytes recién existe desde Android 13.)
     */
    private fun leerConLimite(entrada: InputStream): ByteArray {
        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val leidos = entrada.read(buffer)
            if (leidos < 0) return salida.toByteArray()
            salida.write(buffer, 0, leidos)
            check(salida.size() <= MAXIMO_BYTES) { "Archivo demasiado grande" }
        }
    }

    private class Filas(
        val pacientes: List<PacienteEntity>,
        val turnos: List<TurnoEntity>,
        val bloqueos: List<BloqueoEntity>,
        val cargos: List<CargoSesionEntity>,
        val cobros: List<CobroEntity>,
        val cambios: List<CambioTurnoEntity>,
    )

    private fun RespaldoJson.resumen() = ResumenRespaldo(pacientes = pacientes.size, turnos = turnos.size, cobros = cobros.size)

    private companion object {
        const val MAXIMO_BYTES = 50 * 1024 * 1024
    }
}
