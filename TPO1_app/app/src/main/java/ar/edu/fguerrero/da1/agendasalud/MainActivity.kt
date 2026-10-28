package ar.edu.fguerrero.da1.agendasalud

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.fguerrero.da1.agendasalud.presentation.acceso.LoginScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.AgendaSaludNavHost
import ar.edu.fguerrero.da1.agendasalud.ui.theme.AgendaSaludTheme

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

        val sesion = (application as AgendaSaludApp).container.sesion
        setContent {
            AgendaSaludTheme {
                val desbloqueada by sesion.desbloqueada.collectAsStateWithLifecycle()
                if (desbloqueada) {
                    AgendaSaludNavHost(alCerrarSesion = sesion::bloquear)
                } else {
                    LoginScreen()
                }
            }
        }
    }
}
