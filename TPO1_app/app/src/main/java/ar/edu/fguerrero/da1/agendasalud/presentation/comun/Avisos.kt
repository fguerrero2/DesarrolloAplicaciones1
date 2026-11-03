package ar.edu.fguerrero.da1.agendasalud.presentation.comun

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Mensaje de una sola vez para mostrar en el snackbar. */
sealed interface Aviso {
    data class Exito(@param:StringRes val texto: Int, val args: List<Any> = emptyList()) : Aviso
    data class Error(val error: ErrorDominio) : Aviso
}

/**
 * Base de los ViewModels de pantalla.
 * - Estado persistente → [estadoUi] devuelve un StateFlow (la pantalla siempre ve el último valor).
 * - Eventos de una sola vez (snackbars) → [avisos], un Channel: cada aviso se consume una única vez
 *   y no se repite al rotar o recomponer, igual que un SharedFlow(replay = 0) pero sin perder
 *   avisos emitidos mientras la pantalla no está escuchando.
 */
abstract class ViewModelConAvisos : ViewModel() {
    private val canal = Channel<Aviso>(Channel.BUFFERED)
    val avisos: Flow<Aviso> = canal.receiveAsFlow()

    private val reintentos = MutableStateFlow(0)

    protected fun avisar(aviso: Aviso) {
        canal.trySend(aviso)
    }

    /**
     * Convierte un flujo de datos en [EstadoUi]: Cargando → Contenido, o Error si la consulta falla.
     * [datos] se vuelve a crear con [reintentar]. WhileSubscribed(5 s) corta las consultas cuando
     * la pantalla deja de verse, pero no durante una rotación.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    protected fun <T> estadoUi(datos: () -> Flow<T>): StateFlow<EstadoUi<T>> =
        reintentos.flatMapLatest { intento ->
            datos()
                .map<T, EstadoUi<T>> { EstadoUi.Contenido(it) }
                .onStart { if (intento > 0) emit(EstadoUi.Cargando) }
                .catch { emit(EstadoUi.Error) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoUi.Cargando)

    /** Vuelve a ejecutar las consultas después de un [EstadoUi.Error]. */
    fun reintentar() {
        reintentos.update { it + 1 }
    }

    /** Si una consulta falla, avisa y muestra [alternativo] en vez de cerrar la app. */
    protected fun <T> Flow<T>.avisarSiFalla(alternativo: T): Flow<T> = catch {
        if (it is CancellationException) throw it
        avisar(Aviso.Error(ErrorDominio.ErrorLectura))
        emit(alternativo)
    }

    /** Lanza una operación simple que no devuelve [Resultado]; si falla, avisa con [error]. */
    protected fun lanzar(error: ErrorDominio = ErrorDominio.ErrorGuardado, bloque: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                bloque()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                avisar(Aviso.Error(error))
            }
        }
    }

    /**
     * Ejecuta una operación del dominio. El éxito solo se informa con la escritura confirmada (RNF07).
     * Si [alFallar] es null, el error se muestra en el snackbar; si no, lo maneja quien llamó (por ej. un diálogo).
     */
    protected fun <T> ejecutar(
        operacion: suspend () -> Resultado<T>,
        alFallar: ((ErrorDominio) -> Unit)? = null,
        alExito: suspend (T) -> Unit,
    ) {
        viewModelScope.launch {
            when (val resultado = operacion()) {
                is Resultado.Exito -> alExito(resultado.valor)
                is Resultado.Fallo -> alFallar?.invoke(resultado.error) ?: avisar(Aviso.Error(resultado.error))
            }
        }
    }
}

val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("Falta proveer LocalSnackbar") }

@Composable
fun MostrarAvisos(avisos: Flow<Aviso>) {
    val snackbar = LocalSnackbar.current
    val recursos = LocalResources.current
    LaunchedEffect(avisos) {
        avisos.collect { aviso ->
            val texto = when (aviso) {
                is Aviso.Exito -> recursos.getString(aviso.texto, *aviso.args.toTypedArray())
                is Aviso.Error -> recursos.getString(aviso.error.mensaje())
            }
            snackbar.showSnackbar(texto)
        }
    }
}

@StringRes
fun ErrorDominio.mensaje(): Int = when (this) {
    ErrorDominio.HorarioOcupado -> R.string.error_horario_ocupado
    ErrorDominio.TurnosEnPeriodo -> R.string.error_turnos_en_periodo
    ErrorDominio.PeriodoBloqueado -> R.string.error_periodo_bloqueado
    ErrorDominio.FueraDeHorario -> R.string.error_fuera_de_horario
    ErrorDominio.IntervaloInvalido -> R.string.error_intervalo_invalido
    ErrorDominio.PacienteInactivo -> R.string.error_paciente_inactivo
    ErrorDominio.NombreInvalido -> R.string.error_nombre_invalido
    ErrorDominio.MontoNoPositivo -> R.string.error_monto_no_positivo
    ErrorDominio.MontoSuperaPendiente -> R.string.error_monto_supera_pendiente
    ErrorDominio.EstadoNoPermitido -> R.string.error_estado_no_permitido
    ErrorDominio.NoEncontrado -> R.string.error_no_encontrado
    ErrorDominio.ErrorGuardado -> R.string.error_guardado
    ErrorDominio.ErrorLectura -> R.string.error_lectura
    ErrorDominio.RespaldoInvalido -> R.string.error_respaldo_invalido
    ErrorDominio.ErrorArchivo -> R.string.error_archivo
    ErrorDominio.SinConexion -> R.string.error_sin_conexion
    ErrorDominio.CredencialesInvalidas -> R.string.login_error_credenciales
    ErrorDominio.UsuarioExistente -> R.string.error_usuario_existente
    ErrorDominio.EmailInvalido -> R.string.error_email_invalido
    ErrorDominio.EnlaceVencido -> R.string.error_enlace_vencido
    ErrorDominio.ErrorServidor -> R.string.error_servidor
}
