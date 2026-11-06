package ar.edu.fguerrero.da1.agendasalud.presentation.acceso

import android.util.Patterns
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.security.Credenciales
import ar.edu.fguerrero.da1.agendasalud.core.security.SesionSeguridad
import ar.edu.fguerrero.da1.agendasalud.di.AlcanceApp
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CuentaRepository
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.mensaje
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Qué formulario muestra la pantalla de acceso. Son excluyentes. */
enum class ModoAcceso { INGRESAR, REGISTRARSE, RECUPERAR, NUEVA_CONTRASENIA }

data class LoginUiState(
    val cargando: Boolean = true,
    /** Hay un único usuario por dispositivo: si ya existe, no se ofrece registrarse. */
    val existeUsuario: Boolean = false,
    val modo: ModoAcceso = ModoAcceso.INGRESAR,
    val procesando: Boolean = false,
    @param:StringRes val error: Int? = null,
    /** Mensaje del sistema cuando falla la huella o el PIN (ya viene traducido). */
    val errorDispositivo: String? = null,
    /** Confirmaciones ("te enviamos un mail", "contraseña cambiada"). */
    @param:StringRes val aviso: Int? = null,
)

/**
 * Acceso con la cuenta del backend del equipo (email + contraseña).
 *
 * - **Registrarse**: crea la cuenta en el servidor (necesita internet) y después la local.
 * - **Ingresar en un celular que ya se usó**: se verifica la contraseña LOCALMENTE, así funciona sin
 *   conexión. En segundo plano se conecta con el servidor.
 * - **Ingresar en un celular nuevo**: se valida contra el servidor, se crea la cuenta local y los
 *   datos se descargan con la primera sincronización.
 * - **Olvidé mi contraseña**: el servidor manda un mail; el enlace abre esta pantalla en modo
 *   NUEVA_CONTRASENIA (ver AccesoActivity).
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val credenciales: Credenciales,
    private val sesion: SesionSeguridad,
    private val cuenta: CuentaRepository,
    /** La conexión con el servidor sigue aunque esta pantalla se cierre al entrar a la app. */
    @param:AlcanceApp private val alcanceApp: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /** Token del enlace del mail. Solo en memoria: vence en 30 minutos y sirve una vez. */
    private var tokenRestablecimiento: String? = null

    init {
        viewModelScope.launch {
            val existe = withContext(Dispatchers.IO) { credenciales.existeUsuario() }
            _uiState.update { it.copy(cargando = false, existeUsuario = existe) }
        }
    }

    fun cambiarModo(modo: ModoAcceso) {
        // Registrarse solo si no hay usuario en este celular
        val permitido = if (modo == ModoAcceso.REGISTRARSE) !_uiState.value.existeUsuario else true
        if (permitido) _uiState.update { it.copy(modo = modo, error = null, errorDispositivo = null, aviso = null) }
    }

    /** Llegó el enlace agendasalud://reset-password?token=… del mail. */
    fun recibirEnlace(token: String) {
        tokenRestablecimiento = token
        cambiarModo(ModoAcceso.NUEVA_CONTRASENIA)
    }

    fun crearUsuario(email: String, contrasenia: String, confirmacion: String) {
        val error = validarEmail(email) ?: validarContrasenia(contrasenia, confirmacion)
        if (error != null) return mostrarError(error)
        procesar(entrar = true) {
            when (val remoto = cuenta.registrar(email, contrasenia.toCharArray())) {
                is Resultado.Exito -> crearLocal(email, contrasenia)
                is Resultado.Fallo -> remoto.error.mensaje()
            }
        }
    }

    fun ingresar(email: String, contrasenia: String) {
        if (_uiState.value.existeUsuario) {
            procesar(entrar = true) {
                val ok = withContext(Dispatchers.Default) { credenciales.verificar(email, contrasenia.toCharArray()) }
                if (!ok) return@procesar R.string.login_error_credenciales
                alcanceApp.launch { cuenta.conectarDespuesDeLoginLocal(email, contrasenia.toCharArray()) }
                null
            }
        } else {
            validarEmail(email)?.let { return mostrarError(it) }
            // Celular nuevo: la cuenta tiene que existir en el servidor
            procesar(entrar = true) {
                when (val remoto = cuenta.iniciarSesion(email, contrasenia.toCharArray())) {
                    is Resultado.Exito -> crearLocal(email, contrasenia)
                    is Resultado.Fallo ->
                        if (remoto.error == ErrorDominio.SinConexion) R.string.login_error_primera_vez_sin_conexion else remoto.error.mensaje()
                }
            }
        }
    }

    fun pedirRecuperacion(email: String) {
        validarEmail(email)?.let { return mostrarError(it) }
        procesar(entrar = false) {
            when (val resultado = cuenta.pedirRecuperacion(email)) {
                is Resultado.Exito -> {
                    _uiState.update { it.copy(modo = ModoAcceso.INGRESAR, aviso = R.string.recuperar_enviado) }
                    null
                }
                is Resultado.Fallo -> resultado.error.mensaje()
            }
        }
    }

    fun restablecer(contrasenia: String, confirmacion: String) {
        validarContrasenia(contrasenia, confirmacion)?.let { return mostrarError(it) }
        val token = tokenRestablecimiento ?: return mostrarError(R.string.error_enlace_vencido)
        procesar(entrar = false) {
            when (val resultado = cuenta.restablecerContrasenia(token, contrasenia.toCharArray())) {
                is Resultado.Exito -> {
                    tokenRestablecimiento = null
                    // La contraseña local también cambia, así se puede seguir entrando sin conexión
                    val local = withContext(Dispatchers.IO) { credenciales.usuario() }
                    if (local == resultado.valor.email) {
                        withContext(Dispatchers.Default) { credenciales.reemplazar(local, contrasenia.toCharArray()) }
                    }
                    val aviso = if (resultado.valor.datosConservados) R.string.restablecer_listo else R.string.restablecer_listo_sin_datos
                    _uiState.update { it.copy(modo = ModoAcceso.INGRESAR, aviso = aviso) }
                    null
                }
                is Resultado.Fallo -> resultado.error.mensaje()
            }
        }
    }

    /** Desbloqueo con huella o PIN del dispositivo, solo si ya existe el usuario. */
    fun ingresarConDispositivo() = sesion.desbloquear()

    fun falloDispositivo(mensaje: String) {
        _uiState.update { it.copy(error = null, errorDispositivo = mensaje) }
    }

    /** Guarda el usuario en el celular (hash PBKDF2) para poder ingresar sin conexión la próxima vez. */
    private suspend fun crearLocal(email: String, contrasenia: String): Int? {
        val ok = runCatching { withContext(Dispatchers.Default) { credenciales.crear(email, contrasenia.toCharArray()) } }.isSuccess
        return if (ok) null else R.string.error_guardado
    }

    @StringRes
    private fun validarEmail(email: String): Int? =
        if (Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) null else R.string.error_email_invalido

    @StringRes
    private fun validarContrasenia(contrasenia: String, confirmacion: String): Int? = when {
        contrasenia.length < Credenciales.LARGO_MINIMO -> R.string.login_error_contrasenia_corta
        contrasenia != confirmacion -> R.string.login_error_no_coinciden
        else -> null
    }

    private fun mostrarError(@StringRes error: Int) {
        _uiState.update { it.copy(error = error, aviso = null) }
    }

    /**
     * [operacion] devuelve el texto del error, o null si salió bien. Si [entrar] es true y salió
     * bien, se desbloquea la sesión (AccesoActivity abre la base y pasa a la app).
     */
    private fun procesar(entrar: Boolean, operacion: suspend () -> Int?) {
        _uiState.update { it.copy(procesando = true, error = null, errorDispositivo = null, aviso = null) }
        viewModelScope.launch {
            val error = try {
                operacion()
            } catch (e: Exception) {
                R.string.error_servidor
            }
            when {
                error != null -> _uiState.update { it.copy(procesando = false, error = error) }
                entrar -> {
                    sesion.desbloquear()
                    _uiState.update { it.copy(procesando = false, existeUsuario = true, modo = ModoAcceso.INGRESAR) }
                }
                else -> _uiState.update { it.copy(procesando = false) }
            }
        }
    }
}
