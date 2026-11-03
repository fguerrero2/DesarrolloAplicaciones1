package ar.edu.fguerrero.da1.agendasalud.domain.repository

import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoPendiente
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoSesion
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoSincronizacion
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.model.ResumenRespaldo
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.model.TurnoConPaciente
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

interface PacienteRepository {
    fun observar(busqueda: String = "", incluirInactivos: Boolean = false): Flow<List<Paciente>>
    suspend fun obtener(id: Long): Paciente?
    suspend fun guardar(paciente: Paciente): Resultado<Long>
    suspend fun desactivar(id: Long): Resultado<Unit>
}

interface TurnoRepository {
    fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<TurnoConPaciente>>
    fun observarDePaciente(pacienteId: Long): Flow<List<Turno>>
    suspend fun obtener(id: Long): TurnoConPaciente?

    /** Verifica superposición y bloqueos en la misma transacción que inserta. */
    suspend fun reservar(turno: Turno): Resultado<Long>
    suspend fun reprogramar(turnoId: Long, nuevoInicio: LocalDateTime): Resultado<Unit>
    suspend fun cancelar(turnoId: Long, motivo: String?): Resultado<Unit>

    /** Marca el turno completado y crea su CargoSesion de forma atómica. */
    suspend fun completar(turnoId: Long): Resultado<CargoSesion>
    fun observarCancelacionesDelAnio(pacienteId: Long, anio: Int): Flow<Int>
}

interface BloqueoRepository {
    fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<BloqueoAgenda>>
    suspend fun crear(bloqueo: BloqueoAgenda): Resultado<Long>
    suspend fun eliminar(id: Long)
}

interface CobroRepository {
    fun observarPendientes(): Flow<List<CargoPendiente>>

    /** Todos los cargos, saldados o no, de la sesión más reciente a la más vieja. */
    fun observarCargos(): Flow<List<CargoPendiente>>

    /** Saldo pendiente por paciente (solo pacientes con algún cargo). */
    fun observarSaldos(): Flow<Map<Long, Long>>
    fun observarSaldoPaciente(pacienteId: Long): Flow<Long>
    suspend fun obtenerCargoDeTurno(turnoId: Long): CargoPendiente?

    /** Comprueba el pendiente e inserta el pago en una transacción. */
    suspend fun registrar(
        cargoId: Long,
        montoCentavos: Long,
        fecha: LocalDate,
        medio: MedioCobro,
    ): Resultado<Long>
}

/** Preferencias no sensibles (DataStore). */
interface PreferenciasRepository {
    val duracionPredeterminadaMinutos: Flow<Int>
    val importePredeterminadoCentavos: Flow<Long>
    suspend fun guardarDuracionPredeterminada(minutos: Int)
    suspend fun guardarImportePredeterminado(centavos: Long)
}

/**
 * Respaldo cifrado con una contraseña propia, para recuperar los datos si se pierde el celular.
 * [destino] y [origen] son URIs de archivos que eligió el usuario; la contraseña se borra de memoria al terminar.
 */
interface RespaldoRepository {
    suspend fun exportar(destino: String, contrasenia: CharArray): Resultado<ResumenRespaldo>

    /** Reemplaza TODOS los datos actuales por los del respaldo, en una sola transacción. */
    suspend fun restaurar(origen: String, contrasenia: CharArray): Resultado<ResumenRespaldo>
}

/**
 * Cuenta en el backend del equipo (TPO1_backend). La contraseña nunca se envía: se manda una
 * credencial derivada y la clave de datos viaja envuelta (ver ClavesCuenta).
 */
interface CuentaRepository {
    /** Alta en el servidor. Necesita conexión. */
    suspend fun registrar(email: String, contrasenia: CharArray): Resultado<Unit>

    /** Login en el servidor desde un celular donde la cuenta todavía no existe localmente. */
    suspend fun iniciarSesion(email: String, contrasenia: CharArray): Resultado<Unit>

    /**
     * Después de un login local (sin conexión): deja lista la sesión con el servidor si hay internet.
     * Si la cuenta local es anterior al backend, la crea en el servidor. No falla nunca.
     */
    suspend fun conectarDespuesDeLoginLocal(email: String, contrasenia: CharArray)

    /** El backend manda un mail con un enlace agendasalud://reset-password?token=… */
    suspend fun pedirRecuperacion(email: String): Resultado<Unit>

    /**
     * Cambia la contraseña con el token del mail. Devuelve el email de la cuenta y si se pudieron
     * conservar los datos del servidor (solo si este celular tiene la clave de datos).
     */
    suspend fun restablecerContrasenia(token: String, nueva: CharArray): Resultado<Restablecimiento>

    /** Borra el token del celular. */
    suspend fun cerrarSesion()
}

data class Restablecimiento(val email: String, val datosConservados: Boolean)

/** Sincronización en segundo plano con el backend. Room sigue siendo la fuente de verdad. */
interface SincronizacionRepository {
    val estado: Flow<EstadoSincronizacion>

    /** Programa la sincronización según el calendario del servidor y sincroniza si nunca se hizo. */
    suspend fun alAbrirLaApp()

    /** Anota cuándo cambian los datos locales (para resolver conflictos). Suspende mientras observa. */
    suspend fun registrarCambiosLocales()

    fun sincronizarAhora()
}
