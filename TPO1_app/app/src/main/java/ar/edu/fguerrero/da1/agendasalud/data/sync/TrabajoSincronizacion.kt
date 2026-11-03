package ar.edu.fguerrero.da1.agendasalud.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.remoto.AlmacenCuenta
import ar.edu.fguerrero.da1.agendasalud.data.remoto.ApiAgendaSalud
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoSincronizacion
import ar.edu.fguerrero.da1.agendasalud.domain.repository.SincronizacionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trabajo de WorkManager: corre aunque se cierre la pantalla, respeta las restricciones que define el
 * servidor (wifi, batería) y reintenta solo si no había conexión, hasta max_retry_attempts.
 */
@HiltWorker
class TrabajoSincronizacion @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parametros: WorkerParameters,
    private val sincronizador: Sincronizador,
) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        val resultado = sincronizador.sincronizar()
        val reintentos = inputData.getInt(CLAVE_REINTENTOS, PoliticaSincronizacion().maximoReintentos)
        return if (resultado == EstadoSincronizacion.SinConexion && runAttemptCount < reintentos) Result.retry() else Result.success()
    }

    companion object {
        const val CLAVE_REINTENTOS = "reintentos"
    }
}

/**
 * Política de sincronización que define el backend en sync_config.json (GET /api/v1/sync/schedule).
 * Los valores por defecto son los mismos del archivo, por si el servidor no responde.
 */
data class PoliticaSincronizacion(
    val automatica: Boolean = true,
    val intervaloMinutos: Long = 360,
    val soloWifi: Boolean = true,
    val bateriaNoBaja: Boolean = true,
    val reintentoMinutos: Long = 30,
    val maximoReintentos: Int = 3,
    /** Franja preferida (hora local), por ejemplo de 21 a 23. null si no hay franja. */
    val franja: IntRange? = 21..23,
) {
    companion object {
        fun desde(json: JsonObject): PoliticaSincronizacion {
            val defecto = PoliticaSincronizacion()
            fun bool(clave: String) = json[clave]?.jsonPrimitive?.booleanOrNull
            fun int(clave: String) = json[clave]?.jsonPrimitive?.intOrNull
            val ventana = json["sync_window"]?.jsonObject
            val franja = if (ventana?.get("enabled")?.jsonPrimitive?.booleanOrNull == true) {
                val inicio = ventana["preferred_start_hour_local"]?.jsonPrimitive?.intOrNull
                val fin = ventana["preferred_end_hour_local"]?.jsonPrimitive?.intOrNull
                if (inicio != null && fin != null) inicio..fin else null
            } else {
                null
            }
            return PoliticaSincronizacion(
                automatica = bool("automatic_enabled") ?: defecto.automatica,
                intervaloMinutos = int("interval_minutes")?.toLong() ?: defecto.intervaloMinutos,
                soloWifi = bool("require_unmetered_network") ?: defecto.soloWifi,
                bateriaNoBaja = bool("require_battery_not_low") ?: defecto.bateriaNoBaja,
                reintentoMinutos = int("retry_interval_minutes")?.toLong() ?: defecto.reintentoMinutos,
                maximoReintentos = int("max_retry_attempts") ?: defecto.maximoReintentos,
                franja = franja,
            )
        }
    }
}

/** Programa la sincronización en WorkManager. No necesita la base abierta (se usa también al cerrar sesión). */
@Singleton
class ProgramadorSincronizacion @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val api: ApiAgendaSalud,
) {
    private val trabajos get() = WorkManager.getInstance(context)

    /** Lee el calendario del servidor; si no responde, usa la política por defecto (la misma del archivo). */
    suspend fun programarSegunServidor() {
        val politica = runCatching { PoliticaSincronizacion.desde(api.calendario().politica) }.getOrDefault(PoliticaSincronizacion())
        programar(politica)
    }

    fun programar(politica: PoliticaSincronizacion) {
        if (!politica.automatica) {
            trabajos.cancelUniqueWork(TRABAJO_PERIODICO)
            return
        }
        val restricciones = Constraints.Builder()
            .setRequiredNetworkType(if (politica.soloWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(politica.bateriaNoBaja)
            .build()
        // Android no permite períodos menores a 15 minutos
        val pedido = PeriodicWorkRequestBuilder<TrabajoSincronizacion>(maxOf(politica.intervaloMinutos, 15), TimeUnit.MINUTES)
            .setConstraints(restricciones)
            .setBackoffCriteria(BackoffPolicy.LINEAR, maxOf(politica.reintentoMinutos, 1), TimeUnit.MINUTES)
            .setInitialDelay(demoraHastaFranja(politica.franja), TimeUnit.MINUTES)
            .setInputData(androidx.work.workDataOf(TrabajoSincronizacion.CLAVE_REINTENTOS to politica.maximoReintentos))
            .build()
        // UPDATE: si el servidor cambió la política, se aplica sin duplicar el trabajo
        trabajos.enqueueUniquePeriodicWork(TRABAJO_PERIODICO, ExistingPeriodicWorkPolicy.UPDATE, pedido)
    }

    /** "Sincronizar ahora", primer ingreso en un celular nuevo: con cualquier conexión. */
    fun sincronizarPronto() {
        val pedido = OneTimeWorkRequestBuilder<TrabajoSincronizacion>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        trabajos.enqueueUniqueWork(TRABAJO_AHORA, ExistingWorkPolicy.REPLACE, pedido)
    }

    fun cancelar() {
        trabajos.cancelUniqueWork(TRABAJO_AHORA)
        trabajos.cancelUniqueWork(TRABAJO_PERIODICO)
    }

    /** Minutos hasta el comienzo de la franja preferida (0 si ya estamos dentro o no hay franja). */
    private fun demoraHastaFranja(franja: IntRange?, ahora: LocalDateTime = LocalDateTime.now()): Long {
        if (franja == null || ahora.hour in franja) return 0
        var inicio = ahora.toLocalDate().atTime(franja.first, 0)
        if (!inicio.isAfter(ahora)) inicio = inicio.plusDays(1)
        return Duration.between(ahora, inicio).toMinutes()
    }

    private companion object {
        const val TRABAJO_AHORA = "sincronizar-ahora"
        const val TRABAJO_PERIODICO = "sincronizar-periodica"
    }
}

class SincronizacionRepositoryImpl @Inject constructor(
    private val sincronizador: Sincronizador,
    private val programador: ProgramadorSincronizacion,
    private val almacen: AlmacenCuenta,
    private val db: AgendaDatabase,
) : SincronizacionRepository {

    override val estado: Flow<EstadoSincronizacion> = sincronizador.estado

    override suspend fun alAbrirLaApp() {
        programador.programarSegunServidor()
        // Primer ingreso (por ejemplo, en un celular nuevo): se bajan los datos sin esperar al calendario
        if (almacen.estado().ultimaSincronizacionMs == 0L) programador.sincronizarPronto()
    }

    override suspend fun registrarCambiosLocales() {
        // Cada pantalla tiene su ViewModel: con un solo observador alcanza
        if (!observando.compareAndSet(false, true)) return
        try {
            observarCambios()
        } finally {
            observando.set(false)
        }
    }

    private suspend fun observarCambios() {
        // InvalidationTracker de Room avisa cada vez que cambia alguna de estas tablas.
        // drop(1): la primera emisión es el estado inicial, no un cambio.
        db.invalidationTracker.createFlow(*TABLAS).drop(1).collect {
            almacen.actualizarEstado { it.copy(ultimoCambioLocalMs = System.currentTimeMillis()) }
        }
    }

    override fun sincronizarAhora() = programador.sincronizarPronto()

    private companion object {
        /** Compartido entre instancias del repositorio (se crea uno por ViewModel). */
        val observando = java.util.concurrent.atomic.AtomicBoolean(false)
        val TABLAS = arrayOf("pacientes", "turnos", "cambios_turno", "bloqueos", "cargos_sesion", "cobros")
    }
}

