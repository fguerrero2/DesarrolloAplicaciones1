package ar.edu.fguerrero.da1.agendasalud.core.security

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.view.WindowManager

/**
 * Oculta el contenido en apps recientes y bloquea capturas (RNF03). Se llama en el onCreate de
 * cada Activity. En debug se permiten capturas para poder revisar las pantallas en desarrollo.
 */
fun Activity.protegerPantalla() {
    val esDebug = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (!esDebug) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
}
