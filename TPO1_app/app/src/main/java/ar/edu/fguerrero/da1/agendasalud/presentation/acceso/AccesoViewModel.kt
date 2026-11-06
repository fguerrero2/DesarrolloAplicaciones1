package ar.edu.fguerrero.da1.agendasalud.presentation.acceso

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.agendasalud.core.security.SesionSeguridad
import ar.edu.fguerrero.da1.agendasalud.data.local.BaseDatosCifrada
import ar.edu.fguerrero.da1.agendasalud.data.local.EstadoBase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordina el paso de "login" a "datos listos" en AccesoActivity: apenas la sesión se desbloquea
 * (contraseña o huella) abre la base cifrada, y si falla ofrece reintentar o empezar de cero.
 */
@HiltViewModel
class AccesoViewModel @Inject constructor(
    private val base: BaseDatosCifrada,
    private val sesion: SesionSeguridad,
) : ViewModel() {

    val desbloqueada: StateFlow<Boolean> = sesion.desbloqueada
    val estadoBase: StateFlow<EstadoBase> = base.estado

    init {
        viewModelScope.launch {
            sesion.desbloqueada.filter { it }.collect { base.abrir() }
        }
    }

    fun reintentar() {
        viewModelScope.launch { base.abrir() }
    }

    fun empezarDeCero() {
        viewModelScope.launch { base.empezarDeCero() }
    }

    fun salir() = sesion.bloquear()
}
