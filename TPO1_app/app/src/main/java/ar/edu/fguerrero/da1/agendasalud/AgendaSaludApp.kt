package ar.edu.fguerrero.da1.agendasalud

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import ar.edu.fguerrero.da1.agendasalud.core.security.SesionSeguridad
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** @HiltAndroidApp genera el contenedor de dependencias de toda la app (reemplaza al AppContainer manual). */
@HiltAndroidApp
class AgendaSaludApp : Application() {

    @Inject lateinit var sesion: SesionSeguridad

    override fun onCreate() {
        super.onCreate()
        // Observa el ciclo de vida del proceso (no de una Activity) para el bloqueo a los 2 minutos
        ProcessLifecycleOwner.get().lifecycle.addObserver(sesion)
    }
}
