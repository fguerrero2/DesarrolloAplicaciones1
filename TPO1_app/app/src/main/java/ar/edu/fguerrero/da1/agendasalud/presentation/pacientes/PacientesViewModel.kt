package ar.edu.fguerrero.da1.agendasalud.presentation.pacientes

import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CobroRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PacienteRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.TurnoRepository
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Aviso
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.ViewModelConAvisos
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class PacienteConSaldo(val paciente: Paciente, val saldoCentavos: Long)

data class PacientesUiState(
    val cargando: Boolean = true,
    val pacientes: List<PacienteConSaldo> = emptyList(),
)

/** Wireframe 05 · Ficha del paciente. */
data class FichaPaciente(
    val saldoCentavos: Long,
    val cancelacionesDelAnio: Int,
    val anio: Int,
    val turnos: List<Turno>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PacientesViewModel(
    private val pacientes: PacienteRepository,
    private val turnos: TurnoRepository,
    private val cobros: CobroRepository,
) : ViewModelConAvisos() {

    private val _busqueda = MutableStateFlow("")
    val busqueda: StateFlow<String> = _busqueda.asStateFlow()

    private val _verInactivos = MutableStateFlow(false)
    val verInactivos: StateFlow<Boolean> = _verInactivos.asStateFlow()

    private val seleccionadoId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<PacientesUiState> =
        combine(_busqueda, _verInactivos) { b, i -> b to i }
            .flatMapLatest { (b, inactivos) ->
                combine(pacientes.observar(b, inactivos), cobros.observarSaldos()) { lista, saldos ->
                    PacientesUiState(
                        cargando = false,
                        pacientes = lista.map { PacienteConSaldo(it, saldos[it.id] ?: 0) },
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PacientesUiState())

    val ficha: StateFlow<FichaPaciente?> = seleccionadoId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(null)
        val anio = LocalDate.now().year
        combine(
            cobros.observarSaldoPaciente(id),
            turnos.observarCancelacionesDelAnio(id, anio),
            turnos.observarDePaciente(id),
        ) { saldo, cancelaciones, historial -> FichaPaciente(saldo, cancelaciones, anio, historial) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun buscar(texto: String) {
        _busqueda.value = texto
    }

    fun mostrarInactivos(mostrar: Boolean) {
        _verInactivos.value = mostrar
    }

    fun abrirFicha(id: Long?) {
        seleccionadoId.value = id
    }

    fun guardar(paciente: Paciente, alTerminar: (ErrorDominio?) -> Unit) =
        ejecutar({ pacientes.guardar(paciente) }, alFallar = alTerminar) {
            avisar(Aviso.Exito(if (paciente.id == 0L) R.string.aviso_paciente_creado else R.string.aviso_paciente_actualizado))
            alTerminar(null)
        }

    fun desactivar(id: Long) = ejecutar({ pacientes.desactivar(id) }) {
        seleccionadoId.value = null
        avisar(Aviso.Exito(R.string.aviso_paciente_desactivado))
    }

    fun reactivar(paciente: Paciente) = ejecutar({ pacientes.guardar(paciente.copy(activo = true)) }) {
        avisar(Aviso.Exito(R.string.aviso_paciente_reactivado))
    }
}
