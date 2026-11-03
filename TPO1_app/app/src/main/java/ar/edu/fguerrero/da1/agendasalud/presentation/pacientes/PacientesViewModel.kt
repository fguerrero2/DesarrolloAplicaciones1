package ar.edu.fguerrero.da1.agendasalud.presentation.pacientes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CobroRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PacienteRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.TurnoRepository
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Aviso
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.EstadoUi
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.ViewModelConAvisos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class PacienteConSaldo(val paciente: Paciente, val saldoCentavos: Long)

/** Wireframe 05 · Ficha del paciente. */
data class FichaPaciente(
    val saldoCentavos: Long,
    val cancelacionesDelAnio: Int,
    val anio: Int,
    val turnos: List<Turno>,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class PacientesViewModel @Inject constructor(
    private val pacientes: PacienteRepository,
    private val turnos: TurnoRepository,
    private val cobros: CobroRepository,
    private val estadoGuardado: SavedStateHandle,
) : ViewModelConAvisos() {

    // Búsqueda, filtro y ficha abierta se guardan en el SavedStateHandle: sobreviven a la
    // rotación y a que Android mate el proceso mientras la app está en segundo plano.
    val busqueda: StateFlow<String> = estadoGuardado.getStateFlow(CLAVE_BUSQUEDA, "")
    val verInactivos: StateFlow<Boolean> = estadoGuardado.getStateFlow(CLAVE_INACTIVOS, false)
    val fichaAbierta: StateFlow<Long?> = estadoGuardado.getStateFlow(CLAVE_FICHA, null)

    /**
     * El campo de texto usa [busqueda] al instante; la consulta espera 300 ms sin teclear (debounce)
     * para no ir a la base en cada letra. distinctUntilChanged evita repetir la misma consulta
     * (por ejemplo, "ana" y "ana " buscan lo mismo). Al borrar la búsqueda, la lista vuelve enseguida.
     */
    val uiState: StateFlow<EstadoUi<List<PacienteConSaldo>>> = estadoUi {
        combine(busqueda, verInactivos) { texto, inactivos -> texto.trim() to inactivos }
            .distinctUntilChanged()
            .debounce { (texto, _) -> if (texto.isEmpty()) 0L else ESPERA_BUSQUEDA_MS }
            .flatMapLatest { (texto, inactivos) ->
                combine(pacientes.observar(texto, inactivos), cobros.observarSaldos()) { lista, saldos ->
                    lista.map { PacienteConSaldo(it, saldos[it.id] ?: 0) }
                }
            }
    }

    val ficha: StateFlow<FichaPaciente?> = fichaAbierta.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(null)
        val anio = LocalDate.now().year
        combine(
            cobros.observarSaldoPaciente(id),
            turnos.observarCancelacionesDelAnio(id, anio),
            turnos.observarDePaciente(id),
        ) { saldo, cancelaciones, historial -> FichaPaciente(saldo, cancelaciones, anio, historial) }
    }.avisarSiFalla(null)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun buscar(texto: String) {
        estadoGuardado[CLAVE_BUSQUEDA] = texto
    }

    fun mostrarInactivos(mostrar: Boolean) {
        estadoGuardado[CLAVE_INACTIVOS] = mostrar
    }

    fun abrirFicha(id: Long?) {
        estadoGuardado[CLAVE_FICHA] = id
    }

    fun guardar(paciente: Paciente, alTerminar: (ErrorDominio?) -> Unit) =
        ejecutar({ pacientes.guardar(paciente) }, alFallar = alTerminar) {
            avisar(Aviso.Exito(if (paciente.id == 0L) R.string.aviso_paciente_creado else R.string.aviso_paciente_actualizado))
            alTerminar(null)
        }

    fun desactivar(id: Long) = ejecutar({ pacientes.desactivar(id) }) {
        abrirFicha(null)
        avisar(Aviso.Exito(R.string.aviso_paciente_desactivado))
    }

    fun reactivar(paciente: Paciente) = ejecutar({ pacientes.guardar(paciente.copy(activo = true)) }) {
        avisar(Aviso.Exito(R.string.aviso_paciente_reactivado))
    }

    private companion object {
        const val CLAVE_BUSQUEDA = "busqueda"
        const val CLAVE_INACTIVOS = "verInactivos"
        const val CLAVE_FICHA = "fichaAbierta"
        const val ESPERA_BUSQUEDA_MS = 300L
    }
}
