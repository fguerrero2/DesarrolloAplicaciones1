package ar.edu.fguerrero.da1.agendasalud.data.sync

import ar.edu.fguerrero.da1.agendasalud.core.security.ClavesCuenta
import ar.edu.fguerrero.da1.agendasalud.core.security.DatosCifrados
import ar.edu.fguerrero.da1.agendasalud.data.local.BaseDatosCifrada
import ar.edu.fguerrero.da1.agendasalud.data.local.EstadoBase
import ar.edu.fguerrero.da1.agendasalud.data.remoto.AlmacenCuenta
import ar.edu.fguerrero.da1.agendasalud.data.remoto.ApiAgendaSalud
import ar.edu.fguerrero.da1.agendasalud.data.remoto.CuentaRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.remoto.DatosCifradosDto
import ar.edu.fguerrero.da1.agendasalud.data.remoto.SubidaPedido
import ar.edu.fguerrero.da1.agendasalud.data.respaldo.Instantanea
import ar.edu.fguerrero.da1.agendasalud.data.respaldo.RespaldoJson
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoSincronizacion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincronización con el backend del equipo, offline-first: Room es la única fuente de verdad y las
 * pantallas nunca esperan a la red. Como define el backend, se sincroniza una COPIA COMPLETA de los
 * datos, cifrada en el celular (push) y se recupera la última (pull).
 *
 * Política de conflictos (sync_config.json: LAST_WRITE_WINS_BY_TIMESTAMP):
 * - Si el servidor tiene una versión que este celular no conoce (la subió otro celular):
 *   - sin cambios locales → se baja y reemplaza lo local;
 *   - con cambios locales → gana la copia más reciente: la del servidor si su client_timestamp es
 *     posterior al último cambio local; si no, se sube la local.
 * - Si no, y hay cambios locales, se sube la copia local con versión + 1.
 *
 * Solo trabaja con la base abierta (sesión iniciada): con la sesión cerrada los datos no se tocan.
 */
@Singleton
class Sincronizador @Inject constructor(
    private val base: BaseDatosCifrada,
    private val api: ApiAgendaSalud,
    private val almacen: AlmacenCuenta,
    private val cuenta: CuentaRepositoryImpl,
) {
    // Una sola sincronización a la vez (el worker periódico y "Sincronizar ahora" pueden coincidir)
    private val mutex = Mutex()

    private val _estado = MutableStateFlow<EstadoSincronizacion>(
        almacen.estado().ultimaSincronizacionMs.takeIf { it > 0 }?.let { EstadoSincronizacion.AlDia(it) }
            ?: EstadoSincronizacion.NuncaSincronizado
    )
    val estado: StateFlow<EstadoSincronizacion> = _estado.asStateFlow()

    suspend fun sincronizar(): EstadoSincronizacion = mutex.withLock {
        if (base.estado.value != EstadoBase.Lista) return _estado.value
        val claveDatos = almacen.claveDatos() ?: return terminar(EstadoSincronizacion.SinCuenta)
        if (almacen.sesion() == null && !cuenta.reconectar()) {
            return terminar(if (almacen.credencialEnMemoria == null) EstadoSincronizacion.SinCuenta else EstadoSincronizacion.SinConexion)
        }
        _estado.value = EstadoSincronizacion.Sincronizando
        val resultado = try {
            try {
                sincronizarCon(claveDatos)
            } catch (e: HttpException) {
                // El JWT dura 1 día y el backend no tiene renovación: se pide otro y se reintenta una vez
                if (e.code() != 401) throw e
                almacen.olvidarSesion()
                if (!cuenta.reconectar()) return terminar(EstadoSincronizacion.SinCuenta)
                sincronizarCon(claveDatos)
            }
            EstadoSincronizacion.AlDia(System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            EstadoSincronizacion.SinConexion
        } catch (e: Exception) {
            // Sin detalles: el mensaje podría incluir datos de pacientes
            EstadoSincronizacion.Error
        }
        terminar(resultado)
    }

    private fun terminar(estado: EstadoSincronizacion): EstadoSincronizacion {
        _estado.value = estado
        return estado
    }

    private suspend fun sincronizarCon(claveDatos: ByteArray) {
        val instantanea = Instantanea(base.db)
        val local = instantanea.leer()
        val huellaLocal = Instantanea.huella(local)
        val estado = almacen.estado()
        val hayCambiosLocales = huellaLocal != estado.huella
        val remoto = api.bajar()
        val versionRemota = remoto.version ?: 0
        val ahora = System.currentTimeMillis()

        val payload = remoto.payload
        if (remoto.hayDatos && payload != null && versionRemota > estado.version) {
            val gananRemotos = !hayCambiosLocales || instante(payload.clientTimestamp) > estado.ultimoCambioLocalMs
            if (gananRemotos) {
                val copia = descifrar(claveDatos, payload)
                if (copia != null) {
                    instantanea.restaurar(copia)
                    val huellaNueva = Instantanea.huella(instantanea.leer())
                    almacen.actualizarEstado { it.copy(version = versionRemota, huella = huellaNueva, ultimaSincronizacionMs = ahora) }
                    return
                }
                // No se pudo descifrar (por ejemplo, se restableció la contraseña desde otro celular):
                // la copia del servidor ya no sirve y se reemplaza por la de este celular
            }
        }

        if (hayCambiosLocales || versionRemota > estado.version || !remoto.hayDatos) {
            val version = maxOf(estado.version, versionRemota) + 1
            val cifrado = ClavesCuenta.cifrarDatos(claveDatos, Instantanea.formato.encodeToString(local))
            api.subir(
                SubidaPedido(
                    DatosCifradosDto(
                        version = version,
                        clientTimestamp = Instant.ofEpochMilli(ahora).toString(),
                        ciphertext = cifrado.ciphertext,
                        iv = cifrado.iv,
                        authTag = cifrado.authTag,
                        // Solo cantidades, nunca datos de pacientes
                        metadataResumen = mapOf(
                            "total_pacientes" to local.pacientes.size,
                            "total_turnos" to local.turnos.size,
                            "total_cobros" to local.cobros.size,
                        ),
                    )
                )
            )
            almacen.actualizarEstado { it.copy(version = version, huella = huellaLocal, ultimaSincronizacionMs = ahora) }
        } else {
            almacen.actualizarEstado { it.copy(ultimaSincronizacionMs = ahora) }
        }
    }

    private fun descifrar(claveDatos: ByteArray, payload: DatosCifradosDto): RespaldoJson? = runCatching {
        val json = ClavesCuenta.descifrarDatos(claveDatos, DatosCifrados(payload.ciphertext, payload.iv, payload.authTag))
        Instantanea.formato.decodeFromString<RespaldoJson>(json)
    }.getOrNull()

    /** MongoDB devuelve la fecha sin zona ("2026-11-03T21:00:00"): se asume UTC. */
    private fun instante(texto: String): Long = runCatching { OffsetDateTime.parse(texto).toInstant().toEpochMilli() }
        .getOrElse { LocalDateTime.parse(texto).toInstant(ZoneOffset.UTC).toEpochMilli() }
}
