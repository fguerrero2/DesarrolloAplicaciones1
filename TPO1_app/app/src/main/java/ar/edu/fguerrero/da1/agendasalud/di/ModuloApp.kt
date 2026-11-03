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
import ar.edu.fguerrero.da1.agendasalud.BuildConfig
import ar.edu.fguerrero.da1.agendasalud.data.remoto.AlmacenCuenta
import ar.edu.fguerrero.da1.agendasalud.data.remoto.ApiAgendaSalud
import ar.edu.fguerrero.da1.agendasalud.data.remoto.CuentaRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.data.remoto.InterceptorAutenticacion
import ar.edu.fguerrero.da1.agendasalud.data.sync.SincronizacionRepositoryImpl
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CuentaRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.SincronizacionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
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

/** Alcance de corrutinas de toda la app: para tareas que tienen que terminar aunque se cierre una pantalla. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AlcanceApp

/** Red: Retrofit + OkHttp contra el backend del equipo (URL_SERVIDOR distinta en debug y release). */
@Module
@InstallIn(SingletonComponent::class)
object ModuloRed {

    @Provides
    @Singleton
    @AlcanceApp
    fun alcanceApp(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun api(almacen: AlmacenCuenta): ApiAgendaSalud {
        // Sin interceptor de logging a propósito: los pedidos llevan el token y datos (aunque cifrados).
        // Timeouts generosos: Vercel "despierta" la función en el primer pedido (cold start).
        val cliente = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .addInterceptor(InterceptorAutenticacion(almacen))
            .build()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.URL_SERVIDOR)
            .client(cliente)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiAgendaSalud::class.java)
    }
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
    @Binds abstract fun sincronizacion(impl: SincronizacionRepositoryImpl): SincronizacionRepository

    // CuentaRepositoryImpl es @Singleton y no depende de la base
    @Binds abstract fun cuenta(impl: CuentaRepositoryImpl): CuentaRepository

    @Binds
    @Singleton
    abstract fun preferencias(impl: PreferenciasRepositoryImpl): PreferenciasRepository
}
