package ar.edu.fguerrero.da1.agendasalud.presentation.acceso

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.security.Credenciales
import ar.edu.fguerrero.da1.agendasalud.core.security.SesionSeguridad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LoginUiState(
    val cargando: Boolean = true,
    /** Hay un único usuario por dispositivo: si ya existe, no se ofrece registrarse. */
    val existeUsuario: Boolean = false,
    val registrando: Boolean = false,
    val procesando: Boolean = false,
    @param:StringRes val error: Int? = null,
)

class LoginViewModel(
    private val credenciales: Credenciales,
    private val sesion: SesionSeguridad,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existe = withContext(Dispatchers.IO) { credenciales.existeUsuario() }
            _uiState.value = LoginUiState(cargando = false, existeUsuario = existe)
        }
    }

    fun mostrarRegistro(mostrar: Boolean) {
        _uiState.update { it.copy(registrando = mostrar && !it.existeUsuario, error = null) }
    }

    fun crearUsuario(usuario: String, contrasenia: String, confirmacion: String) {
        val error = when {
            usuario.isBlank() -> R.string.login_error_usuario_vacio
            contrasenia.length < Credenciales.LARGO_MINIMO -> R.string.login_error_contrasenia_corta
            contrasenia != confirmacion -> R.string.login_error_no_coinciden
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        procesar {
            credenciales.crear(usuario, contrasenia.toCharArray())
            true
        }
    }

    fun ingresar(usuario: String, contrasenia: String) {
        if (!_uiState.value.existeUsuario) {
            _uiState.update { it.copy(error = R.string.login_error_sin_usuario) }
            return
        }
        procesar { credenciales.verificar(usuario, contrasenia.toCharArray()) }
    }

    /** Desbloqueo con huella o PIN del dispositivo, solo si ya existe el usuario. */
    fun ingresarConDispositivo() = sesion.desbloquear()

    private fun procesar(operacion: () -> Boolean) {
        _uiState.update { it.copy(procesando = true, error = null) }
        viewModelScope.launch {
            // PBKDF2 es costoso a propósito: fuera del hilo principal
            val ok = runCatching { withContext(Dispatchers.Default) { operacion() } }.getOrDefault(false)
            if (ok) {
                sesion.desbloquear()
                _uiState.update { it.copy(procesando = false, existeUsuario = true, registrando = false) }
            } else {
                _uiState.update { it.copy(procesando = false, error = R.string.login_error_credenciales) }
            }
        }
    }
}
