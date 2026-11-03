package ar.edu.fguerrero.da1.agendasalud.presentation.comun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaNumerica
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearHora
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.rules.HorarioAtencion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Acciones del menú de cuenta que el NavHost pone a disposición de todas las pantallas. */
class AccionesCuenta(
    val exportarRespaldo: () -> Unit,
    val restaurarRespaldo: () -> Unit,
    val cerrarSesion: () -> Unit,
)

val LocalAccionesCuenta = staticCompositionLocalOf<AccionesCuenta?> { null }

/**
 * Encabezado de pantalla como en el wireframe: "AgendaSalud" chico y el título grande.
 * Si hay sesión abierta, muestra arriba a la derecha el menú con respaldo y cerrar sesión.
 */
@Composable
fun Encabezado(titulo: String, modifier: Modifier = Modifier, subtitulo: String? = null) {
    val acciones = LocalAccionesCuenta.current
    Row(modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium)
            Text(titulo, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            subtitulo?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        }
        if (acciones != null) MenuCuenta(acciones)
    }
}

@Composable
private fun MenuCuenta(acciones: AccionesCuenta) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { abierto = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_cuenta))
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.respaldo_exportar)) },
                onClick = {
                    abierto = false
                    acciones.exportarRespaldo()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.respaldo_restaurar)) },
                onClick = {
                    abierto = false
                    acciones.restaurarRespaldo()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cerrar_sesion)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                onClick = {
                    abierto = false
                    acciones.cerrarSesion()
                },
            )
        }
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

enum class EstiloTarjeta { NORMAL, DESTACADA, LIBRE, BLOQUEADA }

/** Tarjeta de lista con una o más líneas de texto. */
@Composable
fun TarjetaTexto(
    lineas: List<String>,
    modifier: Modifier = Modifier,
    estilo: EstiloTarjeta = EstiloTarjeta.NORMAL,
    /** Qué hace tocar la tarjeta; TalkBack lo anuncia ("doble toque para Dar turno"). */
    accion: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colores = MaterialTheme.colorScheme
    val colorFondo = when (estilo) {
        EstiloTarjeta.NORMAL -> colores.surfaceVariant
        EstiloTarjeta.DESTACADA -> colores.primaryContainer
        EstiloTarjeta.LIBRE -> colores.surface
        EstiloTarjeta.BLOQUEADA -> colores.tertiaryContainer
    }
    val cardColors = CardDefaults.cardColors(containerColor = colorFondo)
    val borde = if (estilo == EstiloTarjeta.LIBRE) BorderStroke(1.dp, colores.outlineVariant) else null
    val contenido: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(16.dp)) {
            lineas.forEachIndexed { i, linea ->
                Text(linea, style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (onClick != null) {
        val semantica = if (accion != null) Modifier.semantics { onClick(label = accion) { onClick(); true } } else Modifier
        Card(onClick = onClick, modifier = modifier.fillMaxWidth().then(semantica), colors = cardColors, border = borde) { contenido() }
    }
    else Card(modifier = modifier.fillMaxWidth(), colors = cardColors, border = borde) { contenido() }
}

/** [soloDiasDeAtencion] deshabilita en el calendario los días sin atención (domingos). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoFecha(
    etiqueta: String,
    fecha: LocalDate,
    modifier: Modifier = Modifier,
    soloDiasDeAtencion: Boolean = false,
    alCambiar: (LocalDate) -> Unit,
) {
    // rememberSaveable: si se gira la pantalla con el calendario abierto, sigue abierto
    var abierto by rememberSaveable { mutableStateOf(false) }
    BotonAncho("$etiqueta: ${formatearFechaNumerica(fecha)}", onClick = { abierto = true }, modifier = modifier)
    if (abierto) {
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = fecha.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = if (soloDiasDeAtencion) DiasDeAtencion else DatePickerDefaults.AllDates,
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
private object DiasDeAtencion : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        HorarioAtencion.franja(Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()) != null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoHora(etiqueta: String, hora: LocalTime, modifier: Modifier = Modifier, alCambiar: (LocalTime) -> Unit) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    BotonAncho("$etiqueta: ${formatearHora(hora)}", onClick = { abierto = true }, modifier = modifier)
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
fun SelectorPaciente(
    pacientes: List<Paciente>,
    seleccionado: Paciente?,
    modifier: Modifier = Modifier,
    alElegir: (Paciente) -> Unit,
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    Box(modifier) {
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
fun TextoError(texto: String?, modifier: Modifier = Modifier) {
    texto?.let { Text(it, modifier = modifier, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
}

/** Spinner centrado mientras la pantalla carga sus datos. */
@Composable
fun IndicadorCarga(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** La consulta falló: se dice claramente y se ofrece reintentar, en vez de mostrar una lista vacía. */
@Composable
fun ErrorCarga(alReintentar: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.carga_error), color = MaterialTheme.colorScheme.error)
        BotonAncho(stringResource(R.string.base_reintentar), onClick = alReintentar)
    }
}
