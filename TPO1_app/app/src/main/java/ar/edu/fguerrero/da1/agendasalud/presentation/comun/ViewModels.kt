package ar.edu.fguerrero.da1.agendasalud.presentation.comun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ar.edu.fguerrero.da1.agendasalud.AgendaSaludApp
import ar.edu.fguerrero.da1.agendasalud.AppContainer

/**
 * Crea una factory de ViewModel con acceso al [AppContainer]. Uso:
 * `viewModel<HoyViewModel>(factory = fabricaViewModel { HoyViewModel(it.turnos) })`
 */
inline fun <reified VM : ViewModel> fabricaViewModel(crossinline crear: (AppContainer) -> VM) =
    viewModelFactory {
        initializer { crear(contenedor()) }
    }

fun CreationExtras.contenedor(): AppContainer = (this[APPLICATION_KEY] as AgendaSaludApp).container
