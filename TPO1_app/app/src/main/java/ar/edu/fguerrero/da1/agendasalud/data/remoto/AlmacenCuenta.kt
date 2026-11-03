package ar.edu.fguerrero.da1.agendasalud.data.remoto

import android.content.Context
import ar.edu.fguerrero.da1.agendasalud.core.security.CifradorKeystore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Datos de la sesión con el backend que se guardan en el celular. */
@Serializable
data class SesionRemota(val accessToken: String, val email: String)

/**
 * Estado de la sincronización de este celular.
 * [version] es la última versión del servidor que este celular conoce (subió o bajó).
 * [huella] es un SHA-256 de los datos sincronizados: si los datos actuales dan otra, hay cambios sin subir.
 */
@Serializable
data class EstadoSyncLocal(
    val version: Int = 0,
    val huella: String = "",
    val ultimaSincronizacionMs: Long = 0,
    val ultimoCambioLocalMs: Long = 0,
)

/**
 * Lo que la app guarda de la cuenta remota, siempre cifrado con Android Keystore y en
 * noBackupFilesDir (nunca en SharedPreferences ni en texto plano):
 * - el token de acceso (JWT), que se borra al cerrar sesión;
 * - la clave de datos (DEK), que cifra la copia que se sube. Se conserva al cerrar sesión;
 * - el estado de la sincronización.
 *
 * Además, mientras la sesión está abierta, guarda EN MEMORIA la credencial derivada para pedir un
 * token nuevo si el actual vence (dura 1 día y el backend no tiene renovación). Nunca va a disco.
 */
@Singleton
class AlmacenCuenta @Inject constructor(@ApplicationContext context: Context) {

    private val cifrador = CifradorKeystore("agendasalud_cuenta")
    private val archivoSesion = File(context.noBackupFilesDir, "sesion_remota")
    private val archivoClaveDatos = File(context.noBackupFilesDir, "clave_datos")
    private val archivoEstado = File(context.noBackupFilesDir, "estado_sync")
    private val json = Json { ignoreUnknownKeys = true }

    /** email → credencial derivada. Solo en memoria. */
    @Volatile var credencialEnMemoria: Pair<String, String>? = null

    fun sesion(): SesionRemota? = leer(archivoSesion)
    fun guardarSesion(sesion: SesionRemota) = escribir(archivoSesion, json.encodeToString(sesion))

    /** Token vencido o cierre de sesión. */
    fun olvidarSesion() {
        archivoSesion.delete()
    }

    fun claveDatos(): ByteArray? = runCatching {
        if (archivoClaveDatos.exists()) cifrador.descifrar(archivoClaveDatos.readBytes()) else null
    }.getOrNull()

    fun guardarClaveDatos(clave: ByteArray) {
        archivoClaveDatos.writeBytes(cifrador.cifrar(clave))
    }

    fun estado(): EstadoSyncLocal = leer(archivoEstado) ?: EstadoSyncLocal()

    @Synchronized
    fun actualizarEstado(cambio: (EstadoSyncLocal) -> EstadoSyncLocal) =
        escribir(archivoEstado, json.encodeToString(cambio(estado())))

    private inline fun <reified T> leer(archivo: File): T? = runCatching {
        if (!archivo.exists()) return null
        json.decodeFromString<T>(cifrador.descifrar(archivo.readBytes()).decodeToString())
    }.getOrNull()

    private fun escribir(archivo: File, texto: String) {
        archivo.writeBytes(cifrador.cifrar(texto.encodeToByteArray()))
    }
}
