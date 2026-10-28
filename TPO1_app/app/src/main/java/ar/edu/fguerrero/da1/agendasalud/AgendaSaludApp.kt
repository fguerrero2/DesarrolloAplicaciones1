package ar.edu.fguerrero.da1.agendasalud

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner

class AgendaSaludApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.sesion)
    }
}
