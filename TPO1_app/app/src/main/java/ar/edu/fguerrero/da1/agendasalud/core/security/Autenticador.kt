package ar.edu.fguerrero.da1.agendasalud.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Autenticación del dispositivo: huella, rostro, PIN, patrón o contraseña. */
object Autenticador {

    // BIOMETRIC_WEAK | DEVICE_CREDENTIAL es la combinación admitida en todas las versiones desde API 26
    private const val AUTENTICADORES = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    fun disponible(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTENTICADORES) == BiometricManager.BIOMETRIC_SUCCESS

    fun autenticar(
        activity: FragmentActivity,
        titulo: String,
        alAutenticar: () -> Unit,
        alFallar: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = alAutenticar()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = alFallar(errString.toString())
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titulo)
            .setAllowedAuthenticators(AUTENTICADORES)
            .build()
        prompt.authenticate(info)
    }
}
