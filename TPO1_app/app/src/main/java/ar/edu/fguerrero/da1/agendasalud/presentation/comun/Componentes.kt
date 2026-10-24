package ar.edu.fguerrero.da1.agendasalud.presentation.comun

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaNumerica
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearHora
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Encabezado de pantalla como en el wireframe: "AgendaSalud" chico y el título grande. */
@Composable
fun Encabezado(titulo: String, subtitulo: String? = null) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium)
        Text(titulo, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        subtitulo?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
    }
}

/** Botón de ancho completo y al menos 48 dp de alto (RNF05). */
@Composable
fun BotonAncho(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, habilitado: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text(texto) }
}

/** Tarjeta de lista con una o más líneas de texto. */
@Composable
fun TarjetaTexto(
    lineas: List<String>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val contenido: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(16.dp)) {
            lineas.forEachIndexed { i, linea ->
                Text(linea, style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { contenido() }
    else Card(modifier = modifier.fillMaxWidth()) { contenido() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(etiqueta: String, fecha: LocalDate, alCambiar: (LocalDate) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    BotonAncho("$etiqueta: ${formatearFechaNumerica(fecha)}", onClick = { abierto = true })
    if (abierto) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    // El DatePicker trabaja en milisegundos UTC a medianoche
                    estado.selectedDateMillis?.let { alCambiar(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    abierto = false
                }) { Text(stringResource(R.string.aceptar)) }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text(stringResource(R.string.cancelar)) } },
        ) { DatePicker(estado) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoHora(etiqueta: String, hora: LocalTime, alCambiar: (LocalTime) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    BotonAncho("$etiqueta: ${formatearHora(hora)}", onClick = { abierto = true })
    if (abierto) {
        val estado = rememberTimePickerState(initialHour = hora.hour, initialMinute = hora.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(etiqueta) },
            text = { TimeInput(estado) },
            confirmButton = {
                TextButton(onClick = {
                    alCambiar(LocalTime.of(estado.hour, estado.minute))
                    abierto = false
                }) { Text(stringResource(R.string.aceptar)) }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text(stringResource(R.string.cancelar)) } },
        )
    }
}

@Composable
fun SelectorPaciente(pacientes: List<Paciente>, seleccionado: Paciente?, alElegir: (Paciente) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        BotonAncho(
            stringResource(R.string.campo_paciente, seleccionado?.nombre ?: stringResource(R.string.elegir)),
            onClick = { abierto = true },
        )
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            pacientes.forEach { paciente ->
                DropdownMenuItem(
                    text = { Text(paciente.nombre) },
                    onClick = {
                        alElegir(paciente)
                        abierto = false
                    },
                )
            }
        }
    }
}

/** Mensaje de error dentro de un diálogo. */
@Composable
fun TextoError(texto: String?) {
    texto?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}
