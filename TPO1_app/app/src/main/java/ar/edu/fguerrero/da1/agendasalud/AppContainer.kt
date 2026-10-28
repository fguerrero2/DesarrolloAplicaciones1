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
import java.io.File

/**
 * Inyección de dependencias manual. La base se abre recién cuando alguien la pide,
 * es decir, después de desbloquear la app.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val sesion = SesionSeguridad()
    val credenciales = Credenciales(File(appContext.noBackupFilesDir, "credenciales"))

    private val db: AgendaDatabase by lazy {
        AgendaDatabase.crear(appContext, ClaveBaseDatos(appContext).obtener())
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
