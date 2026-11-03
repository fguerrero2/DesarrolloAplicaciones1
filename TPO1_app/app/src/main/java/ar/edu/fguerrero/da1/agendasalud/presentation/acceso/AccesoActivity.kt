package ar.edu.fguerrero.da1.agendasalud.presentation.acceso

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ar.edu.fguerrero.da1.agendasalud.core.security.protegerPantalla
import ar.edu.fguerrero.da1.agendasalud.data.local.EstadoBase
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.HoyActivity
import ar.edu.fguerrero.da1.agendasalud.ui.theme.AgendaSaludTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Activity de acceso (LAUNCHER): login o registro y apertura de la base cifrada.
 * Cuando los datos están listos abre [HoyActivity], la primera pantalla de la app.
 *
 * Se usa de dos formas:
 * - Al abrir la app: después del login abre HoyActivity y se cierra (finish), así "Atrás" desde
 *   la app no vuelve al login.
 * - Encima de cualquier pantalla de la app ([pedirLogin]): cuando la sesión se bloqueó a los 2 minutos o Android
 *   mató el proceso. Después del login solo se cierra y el usuario vuelve a la pantalla en la que estaba.
 *
 * Extiende FragmentActivity porque BiometricPrompt (ingreso con huella) lo necesita.
 */
@AndroidEntryPoint
class AccesoActivity : FragmentActivity() {

    private val viewModel: AccesoViewModel by viewModels()

    // Misma instancia que usa LoginScreen (hiltViewModel() toma el ViewModelStore de esta Activity)
    private val login: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        protegerPantalla()
        enableEdgeToEdge()

        val sobreLaApp = intent.getBooleanExtra(EXTRA_SOBRE_LA_APP, false)
        manejarEnlace(intent)
        if (sobreLaApp) {
            // Si la app quedó abajo bloqueada, "Atrás" no puede volver a ella sin autenticarse: sale de la app
            onBackPressedDispatcher.addCallback(this) { finishAffinity() }
        }

        // repeatOnLifecycle(STARTED): solo se navega con la Activity visible; se cancela sola en onStop
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.desbloqueada, viewModel.estadoBase) { desbloqueada, estado ->
                    desbloqueada && estado == EstadoBase.Lista
                }.first { it }
                if (!sobreLaApp) {
                    // CLEAR_TASK: si ya había pantallas de la app abiertas (por ejemplo, se abrió el
                    // enlace del mail con la app abierta), se reemplazan en vez de quedar duplicadas
                    startActivity(
                        Intent(this@AccesoActivity, HoyActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }
                finish()
            }
        }

        setContent {
            AgendaSaludTheme {
                val desbloqueada by viewModel.desbloqueada.collectAsStateWithLifecycle()
                val estadoBase by viewModel.estadoBase.collectAsStateWithLifecycle()
                when {
                    !desbloqueada -> LoginScreen()
                    estadoBase == EstadoBase.Error -> ErrorBaseScreen(
                        alReintentar = viewModel::reintentar,
                        alEmpezarDeCero = viewModel::empezarDeCero,
                        alSalir = viewModel::salir,
                    )
                    else -> AbriendoBaseScreen()
                }
            }
        }
    }

    /** singleTop: si el enlace llega con esta pantalla ya abierta, entra por acá. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        manejarEnlace(intent)
    }

    /**
     * Enlace del mail de recuperación (agendasalud://reset-password?token=…). Se cierra la sesión
     * si estaba abierta y se muestra el formulario de nueva contraseña.
     */
    private fun manejarEnlace(intent: Intent) {
        val datos = intent.data ?: return
        if (datos.scheme != "agendasalud" || datos.host != "reset-password") return
        val token = datos.getQueryParameter("token") ?: return
        viewModel.salir()
        login.recibirEnlace(token)
    }

    companion object {
        private const val EXTRA_SOBRE_LA_APP = "sobre_la_app"

        /** Pide login encima de la app, sin perder la pantalla en la que estaba el usuario. */
        fun pedirLogin(context: Context) {
            context.startActivity(Intent(context, AccesoActivity::class.java).putExtra(EXTRA_SOBRE_LA_APP, true))
        }

        /** Cierre de sesión: borra todas las pantallas de la app y deja solo el login. */
        fun cerrarSesion(context: Context) {
            context.startActivity(
                Intent(context, AccesoActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
        }
    }
}
