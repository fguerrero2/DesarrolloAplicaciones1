package ar.edu.fguerrero.da1.agendasalud.presentation.turnos

import androidx.lifecycle.SavedStateHandle
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaLarga
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearHora
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearPesos
import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoPendiente
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.ItemAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.Turno
import ar.edu.fguerrero.da1.agendasalud.domain.repository.BloqueoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CobroRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PacienteRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PreferenciasRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.TurnoRepository
import ar.edu.fguerrero.da1.agendasalud.domain.rules.ArmadoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.rules.HorarioAtencion
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.CompletarSesion
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.RegistrarCobro
import ar.edu.fguerrero.da1.agendasalud.domain.usecase.ReservarTurno
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Aviso
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.EstadoUi
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.ViewModelConAvisos
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** Saldo pendiente de un paciente con turno en el día (wireframe 02: "Pendiente de Ana: $12.000"). */
data class SaldoDelDia(val pacienteId: Long, val nombrePaciente: String, val saldoCentavos: Long)

/** Datos del día ya cargados. La fecha va aparte ([AgendaDiaViewModel.fecha]) porque se muestra aun cargando. */
data class DatosAgendaDia(
    val items: List<ItemAgenda> = emptyList(),
    val pacientesActivos: List<Paciente> = emptyList(),
    val saldosDelDia: List<SaldoDelDia> = emptyList(),
    /** Lo que falta cobrar de cada turno completado (turnoId → centavos). */
    val pendientePorTurno: Map<Long, Long> = emptyMap(),
    /** false los días sin atención (domingos), para diferenciar del día sin huecos libres. */
    val diaDeAtencion: Boolean = true,
    val duracionPredeterminada: Int = 60,
    val importePredeterminado: Long = 0,
)

/** Borrador de WhatsApp tras reprogramar (RF05). El usuario lo revisa y lo envía en WhatsApp. */
data class BorradorWhatsApp(val telefono: String?, val texto: String)

/**
 * Estado y acciones de un día de agenda. Lo usan las pantallas Hoy y Agenda, cada una con su
 * propia instancia (Hilt la ata a la entrada de navegación de cada pantalla).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AgendaDiaViewModel @Inject constructor(
    private val turnos: TurnoRepository,
    private val bloqueos: BloqueoRepository,
    private val pacientes: PacienteRepository,
    private val cobros: CobroRepository,
    private val preferencias: PreferenciasRepository,
    private val reservarTurno: ReservarTurno,
    private val completarSesion: CompletarSesion,
    private val registrarCobro: RegistrarCobro,
    private val estadoGuardado: SavedStateHandle,
) : ViewModelConAvisos() {

    /**
     * Día que se está mirando. Al girar la pantalla lo conserva el ViewModel; si Android mata el
     * proceso en segundo plano, lo recupera el SavedStateHandle (LocalDate es Serializable).
     */
    val fecha: StateFlow<LocalDate> = estadoGuardado.getStateFlow(CLAVE_FECHA, LocalDate.now())

    /** Hora usada para no ofrecer huecos que ya empezaron. Se actualiza cada vez que la pantalla vuelve a verse. */
    private val ahora = MutableStateFlow(LocalDateTime.now())

    private val _cobroEnCurso = MutableStateFlow<CargoPendiente?>(null)
    val cobroEnCurso: StateFlow<CargoPendiente?> = _cobroEnCurso.asStateFlow()

    private val _borradorWhatsApp = MutableStateFlow<BorradorWhatsApp?>(null)
    val borradorWhatsApp: StateFlow<BorradorWhatsApp?> = _borradorWhatsApp.asStateFlow()

    private val valoresPredeterminados =
        combine(preferencias.duracionPredeterminadaMinutos, preferencias.importePredeterminadoCentavos) { d, i -> d to i }

    private val saldosYPendientes = combine(cobros.observarSaldos(), cobros.observarPendientes()) { saldos, cargos ->
        saldos to cargos.associate { it.turnoId to it.pendienteCentavos }
    }

    private val valoresYHora = combine(valoresPredeterminados, ahora) { valores, momento -> valores to momento }

    // flatMapLatest: al cambiar de día se cancela la consulta del día anterior
    val uiState: StateFlow<EstadoUi<DatosAgendaDia>> = estadoUi {
        fecha.flatMapLatest { dia ->
            val desde = dia.atStartOfDay()
            val hasta = dia.plusDays(1).atStartOfDay()
            combine(
                turnos.observarEntre(desde, hasta),
                bloqueos.observarEntre(desde, hasta),
                pacientes.observar(),
                saldosYPendientes,
                valoresYHora,
            ) { turnosDia, bloqueosDia, activos, (saldos, pendientes), (valores, momento) ->
                val (duracion, importe) = valores
                DatosAgendaDia(
                    items = ArmadoAgenda.armar(dia, turnosDia, bloqueosDia, ahora = momento),
                    pacientesActivos = activos,
                    saldosDelDia = turnosDia
                        .distinctBy { it.turno.pacienteId }
                        .mapNotNull { t -> saldos[t.turno.pacienteId]?.takeIf { it > 0 }?.let { SaldoDelDia(t.turno.pacienteId, t.nombrePaciente, it) } },
                    pendientePorTurno = pendientes,
                    diaDeAtencion = HorarioAtencion.franja(dia) != null,
                    duracionPredeterminada = duracion,
                    importePredeterminado = importe,
                )
            }
        }
    }

    fun cambiarFecha(nueva: LocalDate) {
        estadoGuardado[CLAVE_FECHA] = nueva
    }

    /**
     * La pantalla volvió a ser visible (onStart). Se refresca la hora para ocultar huecos que ya
     * pasaron y, en Hoy, se salta al día actual por si la app quedó abierta de un día para otro.
     */
    fun alMostrarse(irAHoy: Boolean) {
        ahora.value = LocalDateTime.now()
        if (irAHoy) cambiarFecha(LocalDate.now())
    }

    fun reservar(
        pacienteId: Long,
        inicio: LocalDateTime,
        duracionMinutos: Int,
        importeCentavos: Long,
        alTerminar: (ErrorDominio?) -> Unit,
    ) = ejecutar(
        operacion = {
            reservarTurno(
                Turno(pacienteId = pacienteId, inicio = inicio, duracionMinutos = duracionMinutos, importeCentavos = importeCentavos)
            )
        },
        alFallar = alTerminar,
    ) {
        // El último importe usado queda como sugerencia para el próximo turno
        preferencias.guardarImportePredeterminado(importeCentavos)
        preferencias.guardarDuracionPredeterminada(duracionMinutos)
        avisar(Aviso.Exito(R.string.aviso_turno_guardado))
        alTerminar(null)
    }

    /** Completa la sesión y, si queda saldo, abre directamente el registro de cobro. */
    fun completar(turnoId: Long) = ejecutar({ completarSesion(turnoId) }) {
        avisar(Aviso.Exito(R.string.aviso_sesion_completada))
        abrirCobro(turnoId)
    }

    fun abrirCobro(turnoId: Long) {
        lanzar(ErrorDominio.ErrorLectura) {
            val cargo = cobros.obtenerCargoDeTurno(turnoId)
            if (cargo == null || cargo.pendienteCentavos <= 0) avisar(Aviso.Exito(R.string.aviso_sesion_saldada))
            else _cobroEnCurso.value = cargo
        }
    }

    fun cerrarCobro() {
        _cobroEnCurso.value = null
    }

    fun registrarCobro(
        montoCentavos: Long,
        fechaCobro: LocalDate,
        medio: MedioCobro,
        alTerminar: (ErrorDominio?) -> Unit,
    ) {
        val cargo = _cobroEnCurso.value ?: return
        ejecutar({ registrarCobro(cargo.cargoId, montoCentavos, medio, fechaCobro) }, alFallar = alTerminar) {
            _cobroEnCurso.value = null
            avisar(avisoCobro(cargo.pendienteCentavos - montoCentavos))
            alTerminar(null)
        }
    }

    fun cancelar(turnoId: Long, motivo: String?) = ejecutar({ turnos.cancelar(turnoId, motivo) }) {
        avisar(Aviso.Exito(R.string.aviso_turno_cancelado))
    }

    fun reprogramar(turnoId: Long, nuevoInicio: LocalDateTime, alTerminar: (ErrorDominio?) -> Unit) =
        ejecutar({ turnos.reprogramar(turnoId, nuevoInicio) }, alFallar = alTerminar) {
            alTerminar(null)
            avisar(Aviso.Exito(R.string.aviso_turno_reprogramado))
            val turno = turnos.obtener(turnoId) ?: return@ejecutar
            val paciente = pacientes.obtener(turno.turno.pacienteId)
            _borradorWhatsApp.value = BorradorWhatsApp(
                telefono = paciente?.telefono,
                texto = "Hola ${turno.nombrePaciente}, te escribo para avisarte que tu turno se reprogramó para el " +
                    "${formatearFechaLarga(nuevoInicio, mayuscula = false)} a las ${formatearHora(nuevoInicio)}. ¡Gracias!",
            )
        }

    fun cerrarWhatsApp() {
        _borradorWhatsApp.value = null
    }

    fun bloquear(inicio: LocalDateTime, fin: LocalDateTime, motivo: String, alTerminar: (ErrorDominio?) -> Unit) =
        ejecutar({ bloqueos.crear(BloqueoAgenda(inicio = inicio, fin = fin, motivo = motivo)) }, alFallar = alTerminar) {
            avisar(Aviso.Exito(R.string.aviso_periodo_bloqueado))
            alTerminar(null)
        }

    fun eliminarBloqueo(id: Long) {
        lanzar {
            bloqueos.eliminar(id)
            avisar(Aviso.Exito(R.string.aviso_bloqueo_eliminado))
        }
    }

    companion object {
        private const val CLAVE_FECHA = "fecha"

        fun avisoCobro(pendienteRestante: Long): Aviso =
            if (pendienteRestante > 0) Aviso.Exito(R.string.aviso_cobro_parcial, listOf(formatearPesos(pendienteRestante)))
            else Aviso.Exito(R.string.aviso_cobro_total)
    }
}
