package ar.edu.fguerrero.da1.agendasalud.presentation.navegacion

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.LocalSnackbar
import ar.edu.fguerrero.da1.agendasalud.presentation.pacientes.PacientesScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.pagos.CobrosScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.AgendaScreen
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.HoyScreen
import kotlin.reflect.KClass

private data class Pestania(val ruta: Any, val clase: KClass<*>, val etiqueta: Int, val icono: ImageVector)

private val pestanias = listOf(
    Pestania(RutaHoy, RutaHoy::class, R.string.nav_hoy, Icons.Filled.Today),
    Pestania(RutaAgenda, RutaAgenda::class, R.string.nav_agenda, Icons.Filled.CalendarMonth),
    Pestania(RutaPacientes, RutaPacientes::class, R.string.nav_pacientes, Icons.Filled.People),
    Pestania(RutaCobros, RutaCobros::class, R.string.nav_cobros, Icons.Filled.Payments),
)

@Composable
fun AgendaSaludNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val entradaActual by navController.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                pestanias.forEach { pestania ->
                    val seleccionada = entradaActual?.destination?.hierarchy?.any { it.hasRoute(pestania.clase) } == true
                    NavigationBarItem(
                        selected = seleccionada,
                        onClick = {
                            navController.navigate(pestania.ruta) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(pestania.icono, contentDescription = null) },
                        label = { Text(stringResource(pestania.etiqueta)) },
                    )
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalSnackbar provides snackbar) {
            NavHost(navController, startDestination = RutaHoy, modifier = Modifier.padding(padding)) {
                composable<RutaHoy> { HoyScreen() }
                composable<RutaAgenda> { AgendaScreen() }
                composable<RutaPacientes> { PacientesScreen() }
                composable<RutaCobros> { CobrosScreen() }
            }
        }
    }
}
