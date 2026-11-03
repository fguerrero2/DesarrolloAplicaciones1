package ar.edu.fguerrero.da1.agendasalud.presentation.navegacion

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import ar.edu.fguerrero.da1.agendasalud.data.sync.ProgramadorSincronizacion
import ar.edu.fguerrero.da1.agendasalud.di.AlcanceApp
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CuentaRepository
import ar.edu.fguerrero.da1.agendasalud.presentation.acceso.AccesoActivity
import ar.edu.fguerrero.da1.agendasalud.ui.theme.AgendaSaludTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Base de las 4 Activities de la app (Hoy, Agenda, Pacientes y Cobros). Cada pantalla es una
 * Activity con su propio ciclo de vida y su propio ViewModel; esta clase tiene lo que comparten:
 * protección de pantalla, control de sesión, barra de navegación, menú ⋮ y cierre de sesión.
 *
 * Hilt inyecta los campos @Inject de esta clase en cada Activity hija (que lleva @AndroidEntryPoint).
 */
abstract class PantallaActivity : ComponentActivity() {

    @Inject lateinit var sesion: SesionSeguridad
    @Inject lateinit var base: BaseDatosCifrada
    @Inject lateinit var cuenta: CuentaRepository
    @Inject lateinit var programador: ProgramadorSincronizacion
    @Inject @AlcanceApp lateinit var alcanceApp: CoroutineScope

    /** Qué pestaña es esta Activity (para marcarla en la barra). */
    protected abstract val pestania: Pestania

    /** Contenido propio de la pantalla. */
    @Composable
    protected abstract fun Contenido()

    private var cerrandoSesion = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        abiertas++
        protegerPantalla()
        enableEdgeToEdge()

        // "Atrás" desde cualquier pestaña sale de la app: las pestañas no son un historial
        onBackPressedDispatcher.addCallback(this) { finishAffinity() }

        // Cada vez que la pantalla vuelve a estar visible (onStart) se verifica la sesión. Si se
        // bloqueó (2 minutos en segundo plano) o Android mató el proceso, se pide el login ENCIMA:
        // al volver, el usuario sigue en esta pantalla. repeatOnLifecycle corta la recolección en onStop.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(sesion.desbloqueada, base.estado) { desbloqueada, estado -> desbloqueada && estado == EstadoBase.Lista }
                    .distinctUntilChanged()
                    .collect { lista -> if (!lista && !cerrandoSesion) AccesoActivity.pedirLogin(this@PantallaActivity) }
            }
        }

        setContent {
            AgendaSaludTheme {
                val estado by base.estado.collectAsStateWithLifecycle()
                // Sin la base abierta no se crea la pantalla (su ViewModel la necesita).
                // Mientras tanto se ve el fondo, tapado por el login.
                if (estado == EstadoBase.Lista) {
                    MarcoPantalla(pestania, alElegir = ::irA, alCerrarSesion = ::cerrarSesion) { Contenido() }
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }
            }
        }
    }

    /**
     * Abre la Activity de otra pestaña. REORDER_TO_FRONT: si ya estaba abierta, la trae adelante tal
     * como quedó (día, filtros, scroll) en vez de crear otra; así nunca hay dos de la misma.
     * Sin animación, para que se sienta como cambiar de pestaña.
     */
    private fun irA(destino: Pestania) {
        if (destino == pestania) return
        val intent = Intent(this, destino.activity.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(intent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle())
    }

    /**
     * Cierre de sesión: se cancela la sincronización programada, se borra el token del backend y
     * se vuelve al login cerrando TODAS las Activities de la app (CLEAR_TASK).
     */
    private fun cerrarSesion() {
        cerrandoSesion = true
        programador.cancelar()
        alcanceApp.launch { cuenta.cerrarSesion() }
        sesion.bloquear()
        AccesoActivity.cerrarSesion(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        abiertas--
        // Cuando se cierra la ÚLTIMA pantalla de la app (cerrar sesión o "Atrás") se cierra la base:
        // la clave deja de estar en memoria. En una rotación isFinishing es false y la base sigue abierta.
        // Para este momento el ViewModel de esta Activity ya se limpió (onCleared).
        if (isFinishing && abiertas == 0) base.cerrar()
    }

    private companion object {
        /** Cuántas pantallas de la app están creadas. Solo un número, nunca una referencia a una Activity. */
        var abiertas = 0
    }
}
