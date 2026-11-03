package ar.edu.fguerrero.da1.agendasalud.data.repository

import android.content.Context
import androidx.core.net.toUri
import ar.edu.fguerrero.da1.agendasalud.core.security.CifradoRespaldo
import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.respaldo.Instantanea
import ar.edu.fguerrero.da1.agendasalud.data.respaldo.RespaldoJson
import ar.edu.fguerrero.da1.agendasalud.data.respaldo.resumen
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.model.ResumenRespaldo
import ar.edu.fguerrero.da1.agendasalud.domain.repository.RespaldoRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
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

    private val instantanea = Instantanea(db)
    private val formato = Instantanea.formato

    override suspend fun exportar(destino: String, contrasenia: CharArray): Resultado<ResumenRespaldo> =
        conContrasenia(contrasenia) {
            val respaldo = instantanea.leer()
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
                formato.decodeFromString<RespaldoJson>(json)
            } catch (e: CifradoRespaldo.RespaldoIlegibleException) {
                return@conContrasenia Resultado.Fallo(ErrorDominio.RespaldoInvalido)
            } catch (e: IllegalArgumentException) {
                // SerializationException hereda de IllegalArgumentException
                return@conContrasenia Resultado.Fallo(ErrorDominio.RespaldoInvalido)
            }

            // Todo o nada: si falla, la base queda como estaba
            try {
                Resultado.Exito(instantanea.restaurar(respaldo))
            } catch (e: IllegalArgumentException) {
                Resultado.Fallo(ErrorDominio.RespaldoInvalido)
            } catch (e: java.time.DateTimeException) {
                // Fecha ilegible dentro de un JSON válido
                Resultado.Fallo(ErrorDominio.RespaldoInvalido)
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



    private companion object {
        const val MAXIMO_BYTES = 50 * 1024 * 1024
    }
}
