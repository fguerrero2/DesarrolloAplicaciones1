package ar.edu.fguerrero.da1.agendasalud.presentation.sincronizacion

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaCorta
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearHora
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoSincronizacion
import ar.edu.fguerrero.da1.agendasalud.domain.repository.SincronizacionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Cómo terminó una sincronización que pidió el usuario, para avisarle en el snackbar.
 * [puedeReintentar] es false cuando reintentar no sirve (hay que volver a ingresar).
 */
sealed interface ResultadoSincronizacion {
    @get:StringRes val mensaje: Int

    data object Exito : ResultadoSincronizacion {
        override val mensaje = R.string.sync_exito
    }

    data class Fallo(@param:StringRes override val mensaje: Int, val puedeReintentar: Boolean) : ResultadoSincronizacion
}

/**
 * Uno por pantalla (Activity). Al abrirse programa la sincronización según el calendario del
 * servidor (es idempotente) y anota cuándo cambian los datos locales (un solo observador a la vez).
 *
 * Heurísticas de Nielsen aplicadas a "Sincronizar ahora":
 * - Visibilidad del estado (H1): [sincronizando] muestra un indicador en todas las pantallas, también
 *   durante las sincronizaciones automáticas.
 * - Prevención de errores (H5): mientras sincroniza, la opción del menú queda deshabilitada.
 * - Resultado (H1, H9): [resultados] avisa el éxito o la causa del error en lenguaje simple, con
 *   "Reintentar" cuando tiene sentido (H3). Solo para pedidos del usuario: las automáticas no
 *   interrumpen con mensajes (H8); su estado queda en el menú ⋮.
 */
@HiltViewModel
class SincronizacionViewModel @Inject constructor(
    private val sincronizacion: SincronizacionRepository,
) : ViewModel() {

    val estado: StateFlow<EstadoSincronizacion> =
        sincronizacion.estado.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoSincronizacion.NuncaSincronizado)

    /** El pedido arranca antes de que el Sincronizador pase a Sincronizando (por ejemplo, al reconectar). */
    private val pedidoEnCurso = MutableStateFlow(false)

    val sincronizando: StateFlow<Boolean> =
        combine(estado, pedidoEnCurso) { e, pedido -> pedido || e == EstadoSincronizacion.Sincronizando }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Sin internet: la pantalla muestra el aviso de modo sin conexión (Nielsen H1). */
    val sinConexion: StateFlow<Boolean> = sincronizacion.conectado.map { !it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val canalResultados = Channel<ResultadoSincronizacion>(Channel.BUFFERED)
    val resultados: Flow<ResultadoSincronizacion> = canalResultados.receiveAsFlow()

    init {
        viewModelScope.launch { sincronizacion.alAbrirLaApp() }
        viewModelScope.launch { sincronizacion.registrarCambiosLocales() }
    }

    fun sincronizarAhora() {
        // Evita pedidos repetidos por doble toque
        if (pedidoEnCurso.value) return
        pedidoEnCurso.value = true
        viewModelScope.launch {
            val resultado = try {
                resultadoDe(sincronizacion.sincronizarAhora())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ResultadoSincronizacion.Fallo(R.string.sync_fallo_error, puedeReintentar = true)
            } finally {
                pedidoEnCurso.value = false
            }
            canalResultados.trySend(resultado)
        }
    }

    companion object {
        fun resultadoDe(estado: EstadoSincronizacion): ResultadoSincronizacion = when (estado) {
            is EstadoSincronizacion.AlDia -> ResultadoSincronizacion.Exito
            EstadoSincronizacion.SinConexion -> ResultadoSincronizacion.Fallo(R.string.sync_fallo_sin_conexion, puedeReintentar = true)
            EstadoSincronizacion.SinCuenta -> ResultadoSincronizacion.Fallo(R.string.sync_fallo_sin_cuenta, puedeReintentar = false)
            EstadoSincronizacion.Error,
            EstadoSincronizacion.NuncaSincronizado,
            EstadoSincronizacion.Sincronizando -> ResultadoSincronizacion.Fallo(R.string.sync_fallo_error, puedeReintentar = true)
        }
    }
}

/** Texto corto para el menú ⋮: "Sincronizado hoy a las 21:04", "Sin conexión", etc. */
@Composable
fun textoSincronizacion(estado: EstadoSincronizacion): String = when (estado) {
    EstadoSincronizacion.Sincronizando -> stringResource(R.string.sync_sincronizando)
    EstadoSincronizacion.SinConexion -> stringResource(R.string.sync_sin_conexion)
    EstadoSincronizacion.SinCuenta -> stringResource(R.string.sync_sin_cuenta)
    EstadoSincronizacion.Error -> stringResource(R.string.sync_error)
    EstadoSincronizacion.NuncaSincronizado -> stringResource(R.string.sync_nunca)
    is EstadoSincronizacion.AlDia -> {
        val momento = Instant.ofEpochMilli(estado.momentoMs).atZone(ZoneId.systemDefault())
        if (momento.toLocalDate() == LocalDate.now()) stringResource(R.string.sync_al_dia_hoy, formatearHora(momento))
        else stringResource(R.string.sync_al_dia, formatearFechaCorta(momento), formatearHora(momento))
    }
}
