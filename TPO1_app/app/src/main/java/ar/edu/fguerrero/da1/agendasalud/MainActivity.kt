package ar.edu.fguerrero.da1.agendasalud

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ar.edu.fguerrero.da1.agendasalud.core.security.SesionSeguridad
import ar.edu.fguerrero.da1.agendasalud.core.security.protegerPantalla
import ar.edu.fguerrero.da1.agendasalud.data.local.BaseDatosCifrada
import ar.edu.fguerrero.da1.agendasalud.data.local.EstadoBase
import ar.edu.fguerrero.da1.agendasalud.presentation.acceso.AccesoActivity
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.AgendaSaludNavHost
import ar.edu.fguerrero.da1.agendasalud.ui.theme.AgendaSaludTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Activity principal: las 4 pantallas de la app (Hoy, Agenda, Pacientes, Cobros) con su barra de
 * navegación. Solo se llega acá desde [AccesoActivity] con la sesión desbloqueada y la base abierta.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sesion: SesionSeguridad
    @Inject lateinit var base: BaseDatosCifrada

    private var cerrandoSesion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        protegerPantalla()
        enableEdgeToEdge()

        // Cada vez que la pantalla vuelve a estar visible se verifica la sesión. Si se bloqueó
        // (2 minutos en segundo plano) o Android mató el proceso, se pide login ENCIMA de esta
        // Activity: al volver, el usuario sigue en la pantalla y el día en que estaba.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(sesion.desbloqueada, base.estado) { desbloqueada, estado -> desbloqueada && estado == EstadoBase.Lista }
                    .distinctUntilChanged()
                    .collect { lista -> if (!lista && !cerrandoSesion) AccesoActivity.pedirLogin(this@MainActivity) }
            }
        }

        setContent {
            AgendaSaludTheme {
                val estado by base.estado.collectAsStateWithLifecycle()
                // Sin la base abierta no se crea ninguna pantalla (sus ViewModels la necesitan).
                // Mientras tanto se ve el fondo, tapado por el login.
                if (estado == EstadoBase.Lista) {
                    AgendaSaludNavHost(alCerrarSesion = ::cerrarSesion)
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            }
        }
    }

    private fun cerrarSesion() {
        cerrandoSesion = true
        sesion.bloquear()
        AccesoActivity.cerrarSesion(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Al salir de verdad (cerrar sesión, "Atrás" o finishAffinity) se cierra la base: la clave
        // deja de estar en memoria. En una rotación (isFinishing = false) la base sigue abierta.
        // Para este momento los ViewModels ya se limpiaron (onCleared), así que nadie la está usando.
        if (isFinishing) base.cerrar()
    }
}
