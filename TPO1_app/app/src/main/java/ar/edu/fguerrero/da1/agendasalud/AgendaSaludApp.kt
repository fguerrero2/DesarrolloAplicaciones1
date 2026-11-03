package ar.edu.fguerrero.da1.agendasalud

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import ar.edu.fguerrero.da1.agendasalud.core.security.SesionSeguridad
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * @HiltAndroidApp genera el contenedor de dependencias de toda la app.
 * Configuration.Provider: WorkManager crea los Workers con la fábrica de Hilt (así reciben el Sincronizador).
 */
@HiltAndroidApp
class AgendaSaludApp : Application(), Configuration.Provider {

    @Inject lateinit var sesion: SesionSeguridad
    @Inject lateinit var fabricaWorkers: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(fabricaWorkers).build()

    override fun onCreate() {
        super.onCreate()
        // Observa el ciclo de vida del proceso (no de una Activity) para el bloqueo a los 2 minutos
        ProcessLifecycleOwner.get().lifecycle.addObserver(sesion)
    }
}
