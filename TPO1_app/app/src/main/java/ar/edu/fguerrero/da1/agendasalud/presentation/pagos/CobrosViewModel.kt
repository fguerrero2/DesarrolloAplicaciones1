package ar.edu.fguerrero.da1.agendasalud.presentation.pagos

import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoPendiente
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CobroRepository
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.RegistrarCobro
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.ViewModelConAvisos
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.AgendaDiaViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class CobrosUiState(
    val cargando: Boolean = true,
    val soloPendientes: Boolean = true,
    val cargos: List<CargoPendiente> = emptyList(),
    val totalPendienteCentavos: Long = 0,
)

class CobrosViewModel(
    cobros: CobroRepository,
    private val registrarCobro: RegistrarCobro,
) : ViewModelConAvisos() {

    private val soloPendientes = MutableStateFlow(true)

    private val _cobroEnCurso = MutableStateFlow<CargoPendiente?>(null)
    val cobroEnCurso: StateFlow<CargoPendiente?> = _cobroEnCurso.asStateFlow()

    val uiState: StateFlow<CobrosUiState> =
        combine(cobros.observarCargos(), soloPendientes) { cargos, pendientes ->
            CobrosUiState(
                cargando = false,
                soloPendientes = pendientes,
                cargos = if (pendientes) cargos.filter { it.pendienteCentavos > 0 } else cargos,
                totalPendienteCentavos = cargos.sumOf { it.pendienteCentavos },
            )
        }.avisarSiFalla(CobrosUiState(cargando = false))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CobrosUiState())

    fun filtrarPendientes(soloPendientes: Boolean) {
        this.soloPendientes.value = soloPendientes
    }

    fun abrirCobro(cargo: CargoPendiente) {
        _cobroEnCurso.value = cargo
    }

    fun cerrarCobro() {
        _cobroEnCurso.value = null
    }

    fun registrarCobro(montoCentavos: Long, fecha: LocalDate, medio: MedioCobro, alTerminar: (ErrorDominio?) -> Unit) {
        val cargo = _cobroEnCurso.value ?: return
        ejecutar({ registrarCobro(cargo.cargoId, montoCentavos, medio, fecha) }, alFallar = alTerminar) {
            _cobroEnCurso.value = null
            avisar(AgendaDiaViewModel.avisoCobro(cargo.pendienteCentavos - montoCentavos))
            alTerminar(null)
        }
    }
}
