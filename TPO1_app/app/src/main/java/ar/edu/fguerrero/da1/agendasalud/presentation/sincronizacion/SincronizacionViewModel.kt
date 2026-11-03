package ar.edu.fguerrero.da1.agendasalud.presentation.sincronizacion

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Vive mientras está abierta la app (a nivel de MainActivity). Al entrar programa la sincronización
 * según el calendario del servidor y anota cuándo cambian los datos locales.
 */
@HiltViewModel
class SincronizacionViewModel @Inject constructor(
    private val sincronizacion: SincronizacionRepository,
) : ViewModel() {

    val estado: StateFlow<EstadoSincronizacion> =
        sincronizacion.estado.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoSincronizacion.NuncaSincronizado)

    init {
        viewModelScope.launch { sincronizacion.alAbrirLaApp() }
        viewModelScope.launch { sincronizacion.registrarCambiosLocales() }
    }

    fun sincronizarAhora() = sincronizacion.sincronizarAhora()
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
