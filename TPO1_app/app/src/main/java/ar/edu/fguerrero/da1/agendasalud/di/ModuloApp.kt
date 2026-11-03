package ar.edu.fguerrero.da1.agendasalud.di

import android.content.Context
import ar.edu.fguerrero.da1.agendasalud.core.security.ClaveBaseDatos
import ar.edu.fguerrero.da1.agendasalud.core.security.Credenciales
import ar.edu.fguerrero.da1.agendasalud.core.security.SesionSeguridad
import ar.edu.fguerrero.da1.agendasalud.data.local.AgendaDatabase
import ar.edu.fguerrero.da1.agendasalud.data.local.BaseDatosCifrada
import ar.edu.fguerrero.da1.agendasalud.data.repository.BloqueoRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.CobroRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.PacienteRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.PreferenciasRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.RespaldoRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.repository.TurnoRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.domain.repository.BloqueoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CobroRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PacienteRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PreferenciasRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.RespaldoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.TurnoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.CompletarSesion
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.RegistrarCobro
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.ReservarTurno
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/**
 * Seguridad: una sola instancia en toda la app. Las clases de core/security no llevan
 * anotaciones de Hilt para poder probarlas en la JVM sin Android.
 */
@Module
@InstallIn(SingletonComponent::class)
object ModuloSeguridad {

    @Provides
    @Singleton
    fun sesion(): SesionSeguridad = SesionSeguridad()

    @Provides
    @Singleton
    fun credenciales(@ApplicationContext context: Context): Credenciales =
        Credenciales(File(context.noBackupFilesDir, "credenciales"))

    @Provides
    @Singleton
    fun claveBaseDatos(@ApplicationContext context: Context): ClaveBaseDatos = ClaveBaseDatos(context)
}

/**
 * Datos. La base NO es @Singleton a propósito: se pide a [BaseDatosCifrada] cada vez que se crea
 * un repositorio, así después de cerrar sesión y volver a entrar los ViewModels nuevos reciben
 * la base recién abierta y nunca una cerrada. Los repositorios son livianos (solo guardan los DAO).
 */
@Module
@InstallIn(SingletonComponent::class)
object ModuloDatos {

    @Provides
    fun agendaDatabase(base: BaseDatosCifrada): AgendaDatabase = base.db

    // Los casos de uso se crean acá para que la capa domain siga sin depender de Hilt
    @Provides
    fun reservarTurno(pacientes: PacienteRepository, turnos: TurnoRepository) = ReservarTurno(pacientes, turnos)

    @Provides
    fun completarSesion(turnos: TurnoRepository) = CompletarSesion(turnos)

    @Provides
    fun registrarCobro(cobros: CobroRepository) = RegistrarCobro(cobros)
}

/** Interfaz del dominio → implementación de la capa data. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloRepositorios {
    @Binds abstract fun pacientes(impl: PacienteRepositoryImpl): PacienteRepository
    @Binds abstract fun turnos(impl: TurnoRepositoryImpl): TurnoRepository
    @Binds abstract fun bloqueos(impl: BloqueoRepositoryImpl): BloqueoRepository
    @Binds abstract fun cobros(impl: CobroRepositoryImpl): CobroRepository
    @Binds abstract fun respaldo(impl: RespaldoRepositoryImpl): RespaldoRepository

    @Binds
    @Singleton
    abstract fun preferencias(impl: PreferenciasRepositoryImpl): PreferenciasRepository
}
