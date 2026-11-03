package ar.edu.fguerrero.da1.agendasalud.presentation.turnos

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaCorta
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaLarga
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearHora
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearPesos
import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.ItemAgenda
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.BotonAncho
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.CampoFecha
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Encabezado
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.ErrorCarga
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.EstadoUi
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.IndicadorCarga
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.datosOrNull
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.EstiloTarjeta
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.MostrarAvisos
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TarjetaTexto
import ar.edu.fguerrero.da1.agendasalud.presentation.disponibilidad.BloquearPeriodoDialog
import ar.edu.fguerrero.da1.agendasalud.presentation.pagos.RegistrarCobroDialog
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Wireframe 02 · Hoy: turnos, huecos y bloqueos del día, más los saldos pendientes de quienes atendés hoy. */
@Composable
fun HoyScreen(modifier: Modifier = Modifier, viewModel: AgendaDiaViewModel = hiltViewModel()) {
    AgendaDia(viewModel, esHoy = true, modifier)
}

/** Wireframe 03 · Agenda del día, con selector de fecha y bloqueo de períodos. */
@Composable
fun AgendaScreen(modifier: Modifier = Modifier, viewModel: AgendaDiaViewModel = hiltViewModel()) {
    AgendaDia(viewModel, esHoy = false, modifier)
}

@Composable
private fun AgendaDia(viewModel: AgendaDiaViewModel, esHoy: Boolean, modifier: Modifier = Modifier) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val fecha by viewModel.fecha.collectAsStateWithLifecycle()
    val cobro by viewModel.cobroEnCurso.collectAsStateWithLifecycle()
    val borrador by viewModel.borradorWhatsApp.collectAsStateWithLifecycle()
    MostrarAvisos(viewModel.avisos)

    // Efecto atado al ciclo de vida real: cada vez que la pantalla vuelve a verse (onStart)
    LifecycleStartEffect(viewModel, esHoy) {
        viewModel.alMostrarse(irAHoy = esHoy)
        onStopOrDispose { }
    }

    val datos = estado.datosOrNull()
    val ocupados = datos?.items.orEmpty().filterIsInstance<ItemAgenda.Ocupado>()
    fun turnoDelDia(id: Long?) = ocupados.firstOrNull { it.turno.turno.id == id }?.turno

    // Qué diálogo está abierto. rememberSaveable: sobreviven a la rotación. Se guardan ids y
    // fechas (tipos que entran en un Bundle) y el objeto se busca en los datos actuales.
    var nuevoTurnoEn by rememberSaveable { mutableStateOf<LocalDateTime?>(null) }
    var turnoSeleccionadoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var reprogramandoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var cancelandoId by rememberSaveable { mutableStateOf<Long?>(null) }
    var bloqueando by rememberSaveable { mutableStateOf(false) }
    var bloqueoAEliminarId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Se toma de la lista actual para que el panel refleje cambios de estado al instante
    val seleccionado = turnoDelDia(turnoSeleccionadoId)

    Column(modifier.fillMaxSize()) {
        if (esHoy) {
            Encabezado(stringResource(R.string.hoy_titulo), subtitulo = formatearFechaLarga(fecha))
        } else {
            Encabezado(stringResource(R.string.agenda_titulo))
            SelectorDia(fecha, alCambiar = viewModel::cambiarFecha)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // when exhaustivo sobre la jerarquía sellada: cargando, error o contenido
            when (estado) {
                EstadoUi.Cargando -> item { IndicadorCarga() }
                EstadoUi.Error -> item { ErrorCarga(alReintentar = viewModel::reintentar) }
                is EstadoUi.Contenido -> if (datos != null && datos.items.isEmpty()) {
                    item {
                        Text(stringResource(if (datos.diaDeAtencion) R.string.agenda_sin_huecos else R.string.agenda_sin_atencion))
                    }
                }
            }

            items(datos?.items.orEmpty(), key = ::claveItem) { item ->
                when (item) {
                    is ItemAgenda.Ocupado -> TarjetaTexto(
                        estilo = if (item.turno.turno.estado == EstadoTurno.CANCELADO) EstiloTarjeta.NORMAL else EstiloTarjeta.DESTACADA,
                        accion = stringResource(R.string.accion_ver_turno),
                        lineas = listOfNotNull(
                            "${formatearHora(item.turno.turno.inicio)}–${formatearHora(item.turno.turno.fin)} · ${item.turno.nombrePaciente}",
                            stringResource(
                                R.string.agenda_sesion_de,
                                item.turno.turno.duracionMinutos,
                                stringResource(etiquetaEstado(item.turno.turno.estado)),
                            ),
                            datos?.pendientePorTurno?.get(item.turno.turno.id)?.let {
                                stringResource(R.string.agenda_pendiente_cobro, formatearPesos(it))
                            },
                        ),
                        onClick = { turnoSeleccionadoId = item.turno.turno.id },
                    )
                    is ItemAgenda.Libre -> TarjetaTexto(
                        estilo = EstiloTarjeta.LIBRE,
                        accion = stringResource(R.string.accion_dar_turno),
                        lineas = listOf(stringResource(R.string.agenda_disponible, formatearHora(item.inicio))),
                        onClick = { nuevoTurnoEn = item.inicio },
                    )
                    is ItemAgenda.Bloqueado -> TarjetaTexto(
                        estilo = EstiloTarjeta.BLOQUEADA,
                        accion = stringResource(R.string.accion_quitar_bloqueo),
                        lineas = listOf(textoBloqueo(item.bloqueo), stringResource(R.string.agenda_horario_bloqueado)),
                        onClick = { bloqueoAEliminarId = item.bloqueo.id },
                    )
                }
            }

            val saldos = datos?.saldosDelDia.orEmpty()
            if (esHoy && saldos.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.hoy_saldos_titulo),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
                items(saldos, key = { "saldo-${it.pacienteId}" }) {
                    TarjetaTexto(listOf(stringResource(R.string.hoy_pendiente_de, it.nombrePaciente, formatearPesos(it.saldoCentavos))))
                }
            }
        }

        // Acciones siempre visibles, sin tener que scrollear la agenda. Se habilitan con los datos cargados.
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotonAncho(
                stringResource(R.string.agenda_nuevo_turno),
                modifier = Modifier.weight(1f),
                habilitado = datos != null,
                onClick = {
                    val primerLibre = datos?.items.orEmpty().filterIsInstance<ItemAgenda.Libre>().firstOrNull()?.inicio
                    nuevoTurnoEn = primerLibre ?: fecha.atTime(LocalTime.of(9, 0))
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

    val inicioNuevoTurno = nuevoTurnoEn
    if (inicioNuevoTurno != null && datos != null) {
        NuevoTurnoDialog(
            pacientes = datos.pacientesActivos,
            fechaInicial = inicioNuevoTurno.toLocalDate(),
            horaInicial = inicioNuevoTurno.toLocalTime(),
            duracionInicial = datos.duracionPredeterminada,
            importeInicial = datos.importePredeterminado,
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
                reprogramandoId = item.turno.id
            },
            alCancelar = {
                turnoSeleccionadoId = null
                cancelandoId = item.turno.id
            },
            alCerrar = { turnoSeleccionadoId = null },
        )
    }

    turnoDelDia(reprogramandoId)?.let { item ->
        ReprogramarDialog(
            item = item,
            alGuardar = { nuevoInicio, alTerminar ->
                viewModel.reprogramar(item.turno.id, nuevoInicio) { error ->
                    alTerminar(error)
                    if (error == null) reprogramandoId = null
                }
            },
            alCerrar = { reprogramandoId = null },
        )
    }

    turnoDelDia(cancelandoId)?.let { item ->
        CancelarTurnoDialog(
            item = item,
            alConfirmar = { motivo ->
                viewModel.cancelar(item.turno.id, motivo)
                cancelandoId = null
            },
            alCerrar = { cancelandoId = null },
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
            fechaInicial = fecha,
            alGuardar = { inicio, fin, motivo, alTerminar ->
                viewModel.bloquear(inicio, fin, motivo) { error ->
                    alTerminar(error)
                    if (error == null) bloqueando = false
                }
            },
            alCerrar = { bloqueando = false },
        )
    }

    val bloqueoAEliminar = datos?.items.orEmpty().filterIsInstance<ItemAgenda.Bloqueado>()
        .firstOrNull { it.bloqueo.id == bloqueoAEliminarId }?.bloqueo
    bloqueoAEliminar?.let { bloqueo ->
        AlertDialog(
            onDismissRequest = { bloqueoAEliminarId = null },
            title = { Text(stringResource(R.string.bloqueo_quitar_titulo)) },
            text = { Text(textoBloqueo(bloqueo)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.eliminarBloqueo(bloqueo.id)
                    bloqueoAEliminarId = null
                }) { Text(stringResource(R.string.bloqueo_quitar)) }
            },
            dismissButton = { TextButton(onClick = { bloqueoAEliminarId = null }) { Text(stringResource(R.string.volver)) } },
        )
    }
}

/**
 * Fila de días: anterior, selector de fecha, siguiente y "Hoy". El botón Hoy va en la misma
 * fila (y no en una propia) para no quitarle altura a la agenda en pantallas chicas o en horizontal.
 */
@Composable
private fun SelectorDia(fecha: LocalDate, alCambiar: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val hoy = LocalDate.now()
    val irAHoy = stringResource(R.string.agenda_ir_a_hoy)
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { alCambiar(fecha.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.agenda_dia_anterior))
        }
        CampoFecha(stringResource(R.string.campo_dia), fecha, modifier = Modifier.weight(1f), alCambiar = alCambiar)
        IconButton(onClick = { alCambiar(fecha.plusDays(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.agenda_dia_siguiente))
        }
        if (fecha != hoy) {
            TextButton(
                onClick = { alCambiar(hoy) },
                modifier = Modifier.semantics { contentDescription = irAHoy },
            ) { Text(stringResource(R.string.agenda_hoy_corto)) }
        }
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
