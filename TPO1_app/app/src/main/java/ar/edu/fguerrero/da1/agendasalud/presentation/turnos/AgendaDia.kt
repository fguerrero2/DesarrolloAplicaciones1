package ar.edu.fguerrero.da1.agendasalud.presentation.turnos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaCorta
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaLarga
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearHora
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearPesos
import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.ItemAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.TurnoConPaciente
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.BotonAncho
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.CampoFecha
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Encabezado
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.EstiloTarjeta
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.MostrarAvisos
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TarjetaTexto
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.fabricaViewModel
import ar.edu.fguerrero.da1.agendasalud.presentation.disponibilidad.BloquearPeriodoDialog
import ar.edu.fguerrero.da1.agendasalud.presentation.pagos.RegistrarCobroDialog
import java.time.LocalDateTime
import java.time.LocalTime

private fun fabricaAgendaDia() = fabricaViewModel {
    AgendaDiaViewModel(
        it.turnos, it.bloqueos, it.pacientes, it.cobros, it.preferencias,
        it.reservarTurno, it.completarSesion, it.registrarCobro,
    )
}

/** Wireframe 02 · Hoy: turnos, huecos y bloqueos del día, más los saldos pendientes de quienes atendés hoy. */
@Composable
fun HoyScreen() {
    AgendaDia(viewModel(key = "hoy", factory = fabricaAgendaDia()), esHoy = true)
}

/** Wireframe 03 · Agenda del día, con selector de fecha y bloqueo de períodos. */
@Composable
fun AgendaScreen() {
    AgendaDia(viewModel(key = "agenda", factory = fabricaAgendaDia()), esHoy = false)
}

@Composable
private fun AgendaDia(viewModel: AgendaDiaViewModel, esHoy: Boolean) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val cobro by viewModel.cobroEnCurso.collectAsStateWithLifecycle()
    val borrador by viewModel.borradorWhatsApp.collectAsStateWithLifecycle()
    MostrarAvisos(viewModel.avisos)

    var nuevoTurnoEn by remember { mutableStateOf<LocalDateTime?>(null) }
    var turnoSeleccionadoId by remember { mutableStateOf<Long?>(null) }
    var reprogramando by remember { mutableStateOf<TurnoConPaciente?>(null) }
    var cancelando by remember { mutableStateOf<TurnoConPaciente?>(null) }
    var bloqueando by remember { mutableStateOf(false) }
    var bloqueoAEliminar by remember { mutableStateOf<BloqueoAgenda?>(null) }

    // Se toma de la lista actual para que el panel refleje cambios de estado al instante
    val seleccionado = estado.items.filterIsInstance<ItemAgenda.Ocupado>()
        .firstOrNull { it.turno.turno.id == turnoSeleccionadoId }?.turno

    Column(Modifier.fillMaxSize()) {
        if (esHoy) {
            Encabezado(stringResource(R.string.hoy_titulo), formatearFechaLarga(estado.fecha))
        } else {
            Encabezado(stringResource(R.string.agenda_titulo))
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.cambiarFecha(estado.fecha.minusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.agenda_dia_anterior))
                }
                Box(Modifier.weight(1f)) {
                    CampoFecha(stringResource(R.string.campo_dia), estado.fecha, viewModel::cambiarFecha)
                }
                IconButton(onClick = { viewModel.cambiarFecha(estado.fecha.plusDays(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.agenda_dia_siguiente))
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (estado.cargando) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            } else if (estado.items.isEmpty()) {
                item { Text(stringResource(R.string.agenda_sin_atencion)) }
            }

            items(estado.items, key = ::claveItem) { item ->
                when (item) {
                    is ItemAgenda.Ocupado -> TarjetaTexto(
                        estilo = if (item.turno.turno.estado == EstadoTurno.CANCELADO) EstiloTarjeta.NORMAL else EstiloTarjeta.DESTACADA,
                        lineas = listOf(
                            "${formatearHora(item.turno.turno.inicio)}–${formatearHora(item.turno.turno.fin)} · ${item.turno.nombrePaciente}",
                            stringResource(
                                R.string.agenda_sesion_de,
                                item.turno.turno.duracionMinutos,
                                stringResource(etiquetaEstado(item.turno.turno.estado)),
                            ),
                        ),
                        onClick = { turnoSeleccionadoId = item.turno.turno.id },
                    )
                    is ItemAgenda.Libre -> TarjetaTexto(
                        estilo = EstiloTarjeta.LIBRE,
                        lineas = listOf(stringResource(R.string.agenda_disponible, formatearHora(item.inicio))),
                        onClick = { nuevoTurnoEn = item.inicio },
                    )
                    is ItemAgenda.Bloqueado -> TarjetaTexto(
                        estilo = EstiloTarjeta.BLOQUEADA,
                        lineas = listOf(textoBloqueo(item.bloqueo), stringResource(R.string.agenda_horario_bloqueado)),
                        onClick = { bloqueoAEliminar = item.bloqueo },
                    )
                }
            }

            if (esHoy) {
                items(estado.saldosDelDia, key = { "saldo-${it.nombrePaciente}" }) {
                    TarjetaTexto(listOf(stringResource(R.string.hoy_pendiente_de, it.nombrePaciente, formatearPesos(it.saldoCentavos))))
                }
            }

        }

        // Acciones siempre visibles, sin tener que scrollear la agenda
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotonAncho(
                stringResource(R.string.agenda_nuevo_turno),
                modifier = Modifier.weight(1f),
                onClick = {
                    val primerLibre = estado.items.filterIsInstance<ItemAgenda.Libre>().firstOrNull()?.inicio
                    nuevoTurnoEn = primerLibre ?: estado.fecha.atTime(LocalTime.of(9, 0))
                },
            )
            if (!esHoy) {
                BotonAncho(
                    stringResource(R.string.agenda_bloquear),
                    modifier = Modifier.weight(1f),
                    onClick = { bloqueando = true },
                )
            }
        }
    }

    nuevoTurnoEn?.let { inicio ->
        NuevoTurnoDialog(
            pacientes = estado.pacientesActivos,
            fechaInicial = inicio.toLocalDate(),
            horaInicial = inicio.toLocalTime(),
            duracionInicial = estado.duracionPredeterminada,
            importeInicial = estado.importePredeterminado,
            alGuardar = { pacienteId, desde, duracion, importe, alTerminar ->
                viewModel.reservar(pacienteId, desde, duracion, importe) { error ->
                    alTerminar(error)
                    if (error == null) nuevoTurnoEn = null
                }
            },
            alCerrar = { nuevoTurnoEn = null },
        )
    }

    seleccionado?.let { item ->
        AccionesTurnoSheet(
            item = item,
            alCompletar = {
                turnoSeleccionadoId = null
                viewModel.completar(item.turno.id)
            },
            alRegistrarCobro = {
                turnoSeleccionadoId = null
                viewModel.abrirCobro(item.turno.id)
            },
            alReprogramar = {
                turnoSeleccionadoId = null
                reprogramando = item
            },
            alCancelar = {
                turnoSeleccionadoId = null
                cancelando = item
            },
            alCerrar = { turnoSeleccionadoId = null },
        )
    }

    reprogramando?.let { item ->
        ReprogramarDialog(
            item = item,
            alGuardar = { nuevoInicio, alTerminar ->
                viewModel.reprogramar(item.turno.id, nuevoInicio) { error ->
                    alTerminar(error)
                    if (error == null) reprogramando = null
                }
            },
            alCerrar = { reprogramando = null },
        )
    }

    cancelando?.let { item ->
        CancelarTurnoDialog(
            item = item,
            alConfirmar = { motivo ->
                viewModel.cancelar(item.turno.id, motivo)
                cancelando = null
            },
            alCerrar = { cancelando = null },
        )
    }

    cobro?.let { cargo ->
        RegistrarCobroDialog(
            cargo = cargo,
            alConfirmar = viewModel::registrarCobro,
            alCerrar = viewModel::cerrarCobro,
        )
    }

    borrador?.let { WhatsAppDialog(it, alCerrar = viewModel::cerrarWhatsApp) }

    if (bloqueando) {
        BloquearPeriodoDialog(
            fechaInicial = estado.fecha,
            alGuardar = { inicio, fin, motivo, alTerminar ->
                viewModel.bloquear(inicio, fin, motivo) { error ->
                    alTerminar(error)
                    if (error == null) bloqueando = false
                }
            },
            alCerrar = { bloqueando = false },
        )
    }

    bloqueoAEliminar?.let { bloqueo ->
        AlertDialog(
            onDismissRequest = { bloqueoAEliminar = null },
            title = { Text(stringResource(R.string.bloqueo_quitar_titulo)) },
            text = { Text(textoBloqueo(bloqueo)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.eliminarBloqueo(bloqueo.id)
                    bloqueoAEliminar = null
                }) { Text(stringResource(R.string.bloqueo_quitar)) }
            },
            dismissButton = { TextButton(onClick = { bloqueoAEliminar = null }) { Text(stringResource(R.string.volver)) } },
        )
    }
}

private fun claveItem(item: ItemAgenda): String = when (item) {
    is ItemAgenda.Ocupado -> "turno-${item.turno.turno.id}"
    is ItemAgenda.Libre -> "libre-${item.inicio}"
    is ItemAgenda.Bloqueado -> "bloqueo-${item.bloqueo.id}"
}

/** "12:00–13:00 · Curso", o con fechas si el bloqueo abarca más de un día. */
private fun textoBloqueo(bloqueo: BloqueoAgenda): String {
    val mismoDia = bloqueo.inicio.toLocalDate() == bloqueo.fin.toLocalDate()
    val rango = if (mismoDia) "${formatearHora(bloqueo.inicio)}–${formatearHora(bloqueo.fin)}"
    else "${formatearFechaCorta(bloqueo.inicio)} ${formatearHora(bloqueo.inicio)} – ${formatearFechaCorta(bloqueo.fin)} ${formatearHora(bloqueo.fin)}"
    return "$rango · ${bloqueo.motivo}"
}
