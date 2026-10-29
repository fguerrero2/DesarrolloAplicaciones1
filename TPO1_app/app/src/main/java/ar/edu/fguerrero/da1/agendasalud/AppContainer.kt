package ar.edu.fguerrero.da1.agendasalud

import android.content.Context
import ar.edu.fguerrero.da1.agendasalud.core.security.ClaveBaseDatos
import ar.edu.fguerrero.da1.agendasalud.core.security.Credenciales
import ar.edu.fguerrero.da1.agendasalud.core.security.SesionSeguridad
import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.repository.BloqueoRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.CobroRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.PacienteRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.PreferenciasRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.TurnoRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.domain.repository.BloqueoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CobroRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PacienteRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PreferenciasRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.TurnoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.CompletarSesion
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.RegistrarCobro
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.ReservarTurno
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Estado de la base cifrada, que se abre recién después del login. */
sealed interface EstadoBase {
    data object Cerrada : EstadoBase
    data object Abriendo : EstadoBase
    data object Lista : EstadoBase
    data object Error : EstadoBase
}

/**
 * Inyección de dependencias manual. La base se abre de forma explícita con [abrirBase]
 * después de desbloquear la app, para poder mostrar un error en lugar de cerrarse si falla.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val sesion = SesionSeguridad()
    val credenciales = Credenciales(File(appContext.noBackupFilesDir, "credenciales"))

    private val clave = ClaveBaseDatos(appContext)
    private var baseAbierta: AgendaDatabase? = null
    private val db: AgendaDatabase get() = checkNotNull(baseAbierta) { "La base todavía no se abrió" }

    private val _estadoBase = MutableStateFlow<EstadoBase>(EstadoBase.Cerrada)
    val estadoBase: StateFlow<EstadoBase> = _estadoBase.asStateFlow()

    /**
     * Abre la base y fuerza una lectura para detectar ya mismo una clave inválida
     * (por ejemplo, si Android Keystore la invalidó). No registra detalles: pueden incluir datos.
     */
    suspend fun abrirBase() {
        if (_estadoBase.value == EstadoBase.Lista || _estadoBase.value == EstadoBase.Abriendo) return
        _estadoBase.value = EstadoBase.Abriendo
        _estadoBase.value = withContext(Dispatchers.IO) {
            try {
                val base = AgendaDatabase.crear(appContext, clave.obtener())
                base.openHelper.writableDatabase
                baseAbierta = base
                EstadoBase.Lista
            } catch (e: Exception) {
                EstadoBase.Error
            }
        }
    }

    /**
     * Último recurso cuando la base no se puede abrir: borra base y clave y crea una vacía.
     * El usuario y la contraseña se conservan. No hay forma de recuperar los datos anteriores.
     */
    suspend fun empezarDeCero() {
        withContext(Dispatchers.IO) {
            baseAbierta?.close()
            baseAbierta = null
            appContext.deleteDatabase(AgendaDatabase.NOMBRE)
            clave.borrar()
        }
        _estadoBase.value = EstadoBase.Cerrada
        abrirBase()
    }

    val pacientes: PacienteRepository by lazy { PacienteRepositoryImpl(db) }
    val turnos: TurnoRepository by lazy { TurnoRepositoryImpl(db) }
    val bloqueos: BloqueoRepository by lazy { BloqueoRepositoryImpl(db) }
    val cobros: CobroRepository by lazy { CobroRepositoryImpl(db) }
    val preferencias: PreferenciasRepository by lazy { PreferenciasRepositoryImpl(appContext) }

    val reservarTurno by lazy { ReservarTurno(pacientes, turnos) }
    val completarSesion by lazy { CompletarSesion(turnos) }
    val registrarCobro by lazy { RegistrarCobro(cobros) }
}
