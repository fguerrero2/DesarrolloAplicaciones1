package ar.edu.fguerrero.da1.agendasalud.core.security

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado de bloqueo de la app (RNF04). Se observa el ciclo de vida del proceso: si la app
 * queda en segundo plano más de [tiempoMaximoMs], vuelve a pedir autenticación.
 */
class SesionSeguridad(
    private val tiempoMaximoMs: Long = 2 * 60 * 1000L,
) : DefaultLifecycleObserver {

    private val _desbloqueada = MutableStateFlow(false)
    val desbloqueada: StateFlow<Boolean> = _desbloqueada.asStateFlow()

    private var enSegundoPlanoDesde: Long? = null

    fun desbloquear() {
        _desbloqueada.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        enSegundoPlanoDesde = SystemClock.elapsedRealtime()
    }

    override fun onStart(owner: LifecycleOwner) {
        val desde = enSegundoPlanoDesde ?: return
        if (SystemClock.elapsedRealtime() - desde > tiempoMaximoMs) _desbloqueada.value = false
        enSegundoPlanoDesde = null
    }
}
