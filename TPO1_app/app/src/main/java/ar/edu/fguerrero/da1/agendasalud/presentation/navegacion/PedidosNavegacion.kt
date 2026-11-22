package ar.edu.fguerrero.da1.agendasalud.presentation.navegacion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pedidos entre pantallas (que son Activities distintas). Por ejemplo, desde un estado vacío de la
 * agenda se pide "Cargar paciente": se abre Pacientes y esta lo atiende abriendo el alta.
 * Es un StateFlow y no un evento: si Pacientes todavía no existía, lo lee al crearse.
 */
@Singleton
class PedidosNavegacion @Inject constructor() {
    private val _nuevoPaciente = MutableStateFlow(false)
    val nuevoPaciente: StateFlow<Boolean> = _nuevoPaciente.asStateFlow()

    fun pedirNuevoPaciente() {
        _nuevoPaciente.value = true
    }

    fun nuevoPacienteAtendido() {
        _nuevoPaciente.value = false
    }
}
