package ar.edu.fguerrero.da1.agendasalud.presentation.pacientes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaCorta
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearHora
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearPesos
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.BotonAncho
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Encabezado
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.ErrorCarga
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.EstadoUi
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.IndicadorCarga
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.MostrarAvisos
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TarjetaTexto
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TextoError
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.datosOrNull
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.mensaje
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.etiquetaEstado

/** Wireframe 04 · Pacientes. La ficha, el alta y la edición se abren desde acá. */
@Composable
fun PacientesScreen(modifier: Modifier = Modifier, viewModel: PacientesViewModel = hiltViewModel()) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val busqueda by viewModel.busqueda.collectAsStateWithLifecycle()
    val verInactivos by viewModel.verInactivos.collectAsStateWithLifecycle()
    val fichaDe by viewModel.fichaAbierta.collectAsStateWithLifecycle()
    MostrarAvisos(viewModel.avisos)

    val lista = estado.datosOrNull().orEmpty()

    // Paciente en edición: 0 = alta nueva. Se guarda el id (entra en un Bundle) para sobrevivir a la rotación.
    var editandoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editando = editandoId?.let { id ->
        if (id == 0L) Paciente(nombre = "") else lista.firstOrNull { it.paciente.id == id }?.paciente
    }

    // Al cerrar un diálogo el foco vuelve al buscador y abriría el teclado sin que nadie lo pida
    val foco = LocalFocusManager.current
    LaunchedEffect(editandoId, fichaDe) {
        if (editandoId == null && fichaDe == null) foco.clearFocus()
    }

    Column(modifier.fillMaxSize()) {
        Encabezado(stringResource(R.string.pacientes_titulo))
        OutlinedTextField(
            value = busqueda,
            onValueChange = viewModel::buscar,
            placeholder = { Text(stringResource(R.string.pacientes_buscar)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        FilterChip(
            selected = verInactivos,
            onClick = { viewModel.mostrarInactivos(!verInactivos) },
            label = { Text(stringResource(R.string.pacientes_ver_inactivos)) },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (estado) {
                EstadoUi.Cargando -> item { IndicadorCarga() }
                EstadoUi.Error -> item { ErrorCarga(alReintentar = viewModel::reintentar) }
                is EstadoUi.Contenido -> if (lista.isEmpty()) {
                    item { Text(stringResource(if (busqueda.isBlank()) R.string.pacientes_vacio else R.string.pacientes_sin_resultados)) }
                }
            }
            items(lista, key = { it.paciente.id }) { item ->
                TarjetaTexto(
                    listOfNotNull(
                        item.paciente.nombre,
                        textoSaldo(item.saldoCentavos),
                        if (!item.paciente.activo) stringResource(R.string.pacientes_inactivo) else null,
                    ),
                    accion = stringResource(R.string.accion_ver_ficha),
                    onClick = { viewModel.abrirFicha(item.paciente.id) },
                )
            }
            item {
                BotonAncho(stringResource(R.string.pacientes_nuevo), onClick = { editandoId = 0L })
            }
        }
    }

    val pacienteFicha = lista.firstOrNull { it.paciente.id == fichaDe }?.paciente
    val ficha by viewModel.ficha.collectAsStateWithLifecycle()
    if (pacienteFicha != null) {
        FichaPacienteSheet(
            paciente = pacienteFicha,
            ficha = ficha,
            alEditar = { editandoId = pacienteFicha.id },
            alDesactivar = { viewModel.desactivar(pacienteFicha.id) },
            alReactivar = { viewModel.reactivar(pacienteFicha) },
            alCerrar = { viewModel.abrirFicha(null) },
        )
    }

    editando?.let { paciente ->
        PacienteDialog(
            paciente = paciente,
            alGuardar = { actualizado, alTerminar ->
                viewModel.guardar(actualizado) { error ->
                    alTerminar(error)
                    if (error == null) editandoId = null
                }
            },
            alCerrar = { editandoId = null },
        )
    }
}

@Composable
private fun textoSaldo(centavos: Long) =
    if (centavos > 0) stringResource(R.string.pacientes_saldo, formatearPesos(centavos))
    else stringResource(R.string.pacientes_sin_saldo)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FichaPacienteSheet(
    paciente: Paciente,
    ficha: FichaPaciente?,
    alEditar: () -> Unit,
    alDesactivar: () -> Unit,
    alReactivar: () -> Unit,
    alCerrar: () -> Unit,
) {
    var confirmandoBaja by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = alCerrar) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(paciente.nombre, style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.ficha_telefono, paciente.telefono ?: stringResource(R.string.ficha_sin_telefono)))
            if (ficha == null) {
                CircularProgressIndicator()
            } else {
                TarjetaTexto(
                    listOf(
                        textoSaldo(ficha.saldoCentavos),
                        stringResource(R.string.ficha_cancelaciones, ficha.anio, ficha.cancelacionesDelAnio),
                    )
                )
                if (ficha.turnos.isEmpty()) Text(stringResource(R.string.ficha_sin_turnos))
                ficha.turnos.take(MAX_TURNOS_FICHA).forEach { turno ->
                    TarjetaTexto(
                        listOf(
                            "${formatearFechaCorta(turno.inicio)} · ${formatearHora(turno.inicio)} · ${stringResource(etiquetaEstado(turno.estado))}",
                            stringResource(R.string.turno_importe, formatearPesos(turno.importeCentavos)),
                        )
                    )
                }
            }
            BotonAncho(stringResource(R.string.ficha_editar), onClick = alEditar)
            if (paciente.activo) BotonAncho(stringResource(R.string.ficha_desactivar), onClick = { confirmandoBaja = true })
            else BotonAncho(stringResource(R.string.ficha_reactivar), onClick = alReactivar)
        }
    }

    if (confirmandoBaja) {
        AlertDialog(
            onDismissRequest = { confirmandoBaja = false },
            title = { Text(stringResource(R.string.ficha_desactivar)) },
            text = { Text(stringResource(R.string.ficha_desactivar_explicacion)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoBaja = false
                    alDesactivar()
                }) { Text(stringResource(R.string.ficha_desactivar_confirmar)) }
            },
            dismissButton = { TextButton(onClick = { confirmandoBaja = false }) { Text(stringResource(R.string.volver)) } },
        )
    }
}

/** Wireframes 06/07 · Nuevo y editar paciente. */
@Composable
private fun PacienteDialog(
    paciente: Paciente,
    alGuardar: (Paciente, alTerminar: (ErrorDominio?) -> Unit) -> Unit,
    alCerrar: () -> Unit,
) {
    var nombre by rememberSaveable { mutableStateOf(paciente.nombre) }
    var telefono by rememberSaveable { mutableStateOf(paciente.telefono.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var guardando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(if (paciente.id == 0L) R.string.pacientes_nuevo_titulo else R.string.ficha_editar)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text(stringResource(R.string.campo_nombre)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
                OutlinedTextField(
                    value = telefono,
                    onValueChange = { telefono = it },
                    label = { Text(stringResource(R.string.campo_telefono_opcional)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                )
                TextoError(error?.let { stringResource(it) })
            }
        },
        confirmButton = {
            TextButton(enabled = !guardando, onClick = {
                guardando = true
                alGuardar(paciente.copy(nombre = nombre, telefono = telefono.ifBlank { null })) { e ->
                    guardando = false
                    error = e?.mensaje()
                }
            }) { Text(stringResource(R.string.guardar)) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

private const val MAX_TURNOS_FICHA = 20
