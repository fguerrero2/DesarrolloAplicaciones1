package ar.edu.fguerrero.da1.agendasalud.presentation.navegacion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.presentation.ayuda.AyudaDialog
import ar.edu.fguerrero.da1.agendasalud.presentation.ayuda.AyudaViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.AccionesCuenta
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.LocalAccionesCuenta
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.LocalSnackbar
import ar.edu.fguerrero.da1.agendasalud.presentation.respaldo.RespaldoDialogos
import ar.edu.fguerrero.da1.agendasalud.presentation.respaldo.RespaldoViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.sincronizacion.AvisosSincronizacion
import ar.edu.fguerrero.da1.agendasalud.presentation.sincronizacion.SincronizacionViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.sincronizacion.textoSincronizacion

/**
 * Franja visible mientras no hay internet (Nielsen H1): avisa que se puede seguir trabajando y que
 * nada se pierde. TalkBack la anuncia al aparecer.
 */
@Composable
private fun AvisoSinConexion() {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(R.drawable.ic_sin_conexion), contentDescription = null)
            Text(stringResource(R.string.sin_conexion_banner), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Ancho máximo del contenido: en tablets las tarjetas no se estiran de borde a borde. */
private val ANCHO_MAXIMO_CONTENIDO = 720.dp

/**
 * Marco común de las 4 pantallas: barra de navegación, snackbar, menú ⋮ y diálogos de respaldo.
 *
 * Responsive: en un celular vertical la navegación va abajo (NavigationBar). En horizontal o en
 * tablet va al costado (NavigationRail), porque la altura es lo que falta.
 */
@Composable
fun MarcoPantalla(
    actual: Pestania,
    alElegir: (Pestania) -> Unit,
    alCerrarSesion: () -> Unit,
    alCargarPaciente: () -> Unit,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    // Tamaño real de la ventana (en pantalla dividida es menor que la pantalla del celular)
    val ventana = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) } }
    val navegacionLateral = ventana.width >= 600.dp || ventana.height < 480.dp

    // ViewModels de esta Activity para el menú ⋮ (se limpian con onCleared al cerrarla)
    val respaldo: RespaldoViewModel = hiltViewModel()
    val sincronizacion: SincronizacionViewModel = hiltViewModel()
    val estadoSync by sincronizacion.estado.collectAsStateWithLifecycle()
    val sincronizando by sincronizacion.sincronizando.collectAsStateWithLifecycle()
    val sinConexion by sincronizacion.sinConexion.collectAsStateWithLifecycle()
    val ayuda: AyudaViewModel = hiltViewModel()
    val modoAyuda by ayuda.modo.collectAsStateWithLifecycle()
    val textoSync = textoSincronizacion(estadoSync)
    val acciones = remember(respaldo, sincronizacion, ayuda, alCerrarSesion, alCargarPaciente, textoSync, sincronizando) {
        AccionesCuenta(
            estadoSincronizacion = textoSync,
            sincronizando = sincronizando,
            sincronizar = sincronizacion::sincronizarAhora,
            exportarRespaldo = respaldo::pedirExportar,
            restaurarRespaldo = respaldo::pedirRestaurar,
            cerrarSesion = alCerrarSesion,
            abrirAyuda = ayuda::abrir,
            cargarPaciente = alCargarPaciente,
        )
    }

    Row(modifier.fillMaxSize()) {
        if (navegacionLateral) {
            NavigationRail {
                Pestania.entries.forEach { pestania ->
                    NavigationRailItem(
                        selected = pestania == actual,
                        onClick = { alElegir(pestania) },
                        icon = { Icon(painterResource(pestania.icono), contentDescription = null) },
                        label = { Text(stringResource(pestania.etiqueta)) },
                    )
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (!navegacionLateral) {
                    NavigationBar {
                        Pestania.entries.forEach { pestania ->
                            NavigationBarItem(
                                selected = pestania == actual,
                                onClick = { alElegir(pestania) },
                                icon = { Icon(painterResource(pestania.icono), contentDescription = null) },
                                label = { Text(stringResource(pestania.etiqueta)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            CompositionLocalProvider(LocalSnackbar provides snackbar, LocalAccionesCuenta provides acciones) {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (sinConexion) AvisoSinConexion()
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.widthIn(max = ANCHO_MAXIMO_CONTENIDO)) { contenido() }
                    }
                }
                RespaldoDialogos(respaldo)
                AvisosSincronizacion(sincronizacion)
                modoAyuda?.let { AyudaDialog(it, alCerrar = ayuda::cerrar) }
            }
        }
    }
}
