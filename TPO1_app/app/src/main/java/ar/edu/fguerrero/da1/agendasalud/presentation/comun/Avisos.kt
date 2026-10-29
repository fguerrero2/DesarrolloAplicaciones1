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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Mensaje de una sola vez para mostrar en el snackbar. */
sealed interface Aviso {
    data class Exito(@param:StringRes val texto: Int, val args: List<Any> = emptyList()) : Aviso
    data class Error(val error: ErrorDominio) : Aviso
}

abstract class ViewModelConAvisos : ViewModel() {
    private val canal = Channel<Aviso>(Channel.BUFFERED)
    val avisos: Flow<Aviso> = canal.receiveAsFlow()

    protected fun avisar(aviso: Aviso) {
        canal.trySend(aviso)
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
}
