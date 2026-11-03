package ar.edu.fguerrero.da1.agendasalud.presentation.navegacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.AccionesCuenta
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.LocalAccionesCuenta
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.LocalSnackbar
import ar.edu.fguerrero.da1.agendasalud.presentation.pacientes.PacientesScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.pagos.CobrosScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.respaldo.RespaldoDialogos
import ar.edu.fguerrero.da1.agendasalud.presentation.respaldo.RespaldoViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.AgendaScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.HoyScreen
import kotlin.reflect.KClass

private data class Pestania(val ruta: Any, val clase: KClass<*>, val etiqueta: Int, val icono: Int)

private val pestanias = listOf(
    Pestania(RutaHoy, RutaHoy::class, R.string.nav_hoy, R.drawable.ic_hoy),
    Pestania(RutaAgenda, RutaAgenda::class, R.string.nav_agenda, R.drawable.ic_agenda),
    Pestania(RutaPacientes, RutaPacientes::class, R.string.nav_pacientes, R.drawable.ic_pacientes),
    Pestania(RutaCobros, RutaCobros::class, R.string.nav_cobros, R.drawable.ic_cobros),
)

/** Ancho máximo del contenido: en tablets las tarjetas no se estiran de borde a borde. */
private val ANCHO_MAXIMO_CONTENIDO = 720.dp

/**
 * Las 4 pantallas de la app con su barra de navegación.
 *
 * Responsive: en un celular vertical la navegación va abajo (NavigationBar). En horizontal o en
 * tablet va al costado (NavigationRail), porque la altura es lo que falta y una barra inferior
 * le quitaría 80 dp a la agenda.
 */
@Composable
fun AgendaSaludNavHost(alCerrarSesion: () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val entradaActual by navController.currentBackStackEntryAsState()
    val destino = entradaActual?.destination
    val snackbar = remember { SnackbarHostState() }
    // Tamaño real de la ventana (en pantalla dividida es menor que la pantalla del celular)
    val ventana = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.let { DpSize(it.width.toDp(), it.height.toDp()) } }
    val navegacionLateral = ventana.width >= 600.dp || ventana.height < 480.dp

    // A nivel de Activity: el respaldo se abre desde el menú de cualquiera de las 4 pantallas
    val respaldo: RespaldoViewModel = hiltViewModel()
    val acciones = remember(respaldo, alCerrarSesion) {
        AccionesCuenta(
            exportarRespaldo = respaldo::pedirExportar,
            restaurarRespaldo = respaldo::pedirRestaurar,
            cerrarSesion = alCerrarSesion,
        )
    }

    Row(modifier.fillMaxSize()) {
        if (navegacionLateral) {
            NavigationRail {
                pestanias.forEach { pestania ->
                    NavigationRailItem(
                        selected = destino.esDe(pestania),
                        onClick = { navController.irA(pestania) },
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
                        pestanias.forEach { pestania ->
                            NavigationBarItem(
                                selected = destino.esDe(pestania),
                                onClick = { navController.irA(pestania) },
                                icon = { Icon(painterResource(pestania.icono), contentDescription = null) },
                                label = { Text(stringResource(pestania.etiqueta)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            CompositionLocalProvider(LocalSnackbar provides snackbar, LocalAccionesCuenta provides acciones) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    NavHost(
                        navController,
                        startDestination = RutaHoy,
                        modifier = Modifier.widthIn(max = ANCHO_MAXIMO_CONTENIDO),
                    ) {
                        composable<RutaHoy> { HoyScreen() }
                        composable<RutaAgenda> { AgendaScreen() }
                        composable<RutaPacientes> { PacientesScreen() }
                        composable<RutaCobros> { CobrosScreen() }
                    }
                }
                RespaldoDialogos(respaldo)
            }
        }
    }
}

private fun NavDestination?.esDe(pestania: Pestania) = this?.hierarchy?.any { it.hasRoute(pestania.clase) } == true

/** Cambia de pestaña conservando el estado de cada una (scroll, filtros) y sin apilar pantallas. */
private fun NavHostController.irA(pestania: Pestania) {
    navigate(pestania.ruta) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
