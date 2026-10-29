package ar.edu.fguerrero.da1.agendasalud

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.fguerrero.da1.agendasalud.presentation.acceso.AbriendoBaseScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.acceso.ErrorBaseScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.acceso.LoginScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.AgendaSaludNavHost
import ar.edu.fguerrero.da1.agendasalud.ui.theme.AgendaSaludTheme
import kotlinx.coroutines.launch

// FragmentActivity es requerida por BiometricPrompt
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Oculta el contenido en apps recientes y bloquea capturas (RNF03).
        // En debug se permiten capturas para poder revisar las pantallas en desarrollo.
        val esDebug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!esDebug) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()

        val container = (application as AgendaSaludApp).container
        val sesion = container.sesion
        setContent {
            AgendaSaludTheme {
                val desbloqueada by sesion.desbloqueada.collectAsStateWithLifecycle()
                val estadoBase by container.estadoBase.collectAsStateWithLifecycle()
                val scope = rememberCoroutineScope()

                // La base cifrada se abre recién después del login
                LaunchedEffect(desbloqueada) {
                    if (desbloqueada) container.abrirBase()
                }

                when {
                    !desbloqueada -> LoginScreen()
                    estadoBase == EstadoBase.Lista -> AgendaSaludNavHost(alCerrarSesion = sesion::bloquear)
                    estadoBase == EstadoBase.Error -> ErrorBaseScreen(
                        alReintentar = { scope.launch { container.abrirBase() } },
                        alEmpezarDeCero = { scope.launch { container.empezarDeCero() } },
                        alSalir = sesion::bloquear,
                    )
                    else -> AbriendoBaseScreen()
                }
            }
        }
    }
}
