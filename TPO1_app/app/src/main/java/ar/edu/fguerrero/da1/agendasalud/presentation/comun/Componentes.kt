package ar.edu.fguerrero.da1.agendasalud.presentation.comun

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
data class AccionesCuenta(
    /** Estado de la sincronización con el servidor, ya como texto ("Sincronizado a las 10:42"). */
    val estadoSincronizacion: String,
    /** Hay una sincronización en curso (pedida por el usuario o automática). */
    val sincronizando: Boolean,
    val sincronizar: () -> Unit,
    val exportarRespaldo: () -> Unit,
    val restaurarRespaldo: () -> Unit,
    val cerrarSesion: () -> Unit,
    /** Abre la ayuda "¿Cómo funciona?" (Nielsen H10). */
    val abrirAyuda: () -> Unit,
    /** Lleva a Pacientes con el alta abierta (para los estados vacíos sin pacientes). */
    val cargarPaciente: () -> Unit,
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
        if (acciones != null) {
            // Visibilidad del estado del sistema: se ve en todas las pantallas sin abrir el menú
            if (acciones.sincronizando) IndicadorSincronizando(Modifier.align(Alignment.CenterVertically))
            MenuCuenta(acciones)
        }
    }
}

@Composable
private fun IndicadorSincronizando(modifier: Modifier = Modifier) {
    val descripcion = stringResource(R.string.sync_en_curso)
    CircularProgressIndicator(
        strokeWidth = 2.dp,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .size(20.dp)
            // TalkBack lo anuncia al aparecer
            .semantics {
                contentDescription = descripcion
                liveRegion = LiveRegionMode.Polite
            },
    )
}

@Composable
private fun MenuCuenta(acciones: AccionesCuenta) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    var confirmandoCierre by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { abierto = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.menu_cuenta))
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            // Solo informativo: deshabilitado para que no parezca un botón
            DropdownMenuItem(
                text = { Text(acciones.estadoSincronizacion, style = MaterialTheme.typography.bodySmall) },
                enabled = false,
                onClick = {},
            )
            // Deshabilitada mientras sincroniza: evita pedidos repetidos y muestra que está trabajando
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (acciones.sincronizando) R.string.sync_sincronizando else R.string.sync_ahora))
                },
                leadingIcon = if (acciones.sincronizando) {
                    { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                enabled = !acciones.sincronizando,
                onClick = {
                    abierto = false
                    acciones.sincronizar()
                },
            )
            HorizontalDivider()
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
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ayuda_menu)) },
                onClick = {
                    abierto = false
                    acciones.abrirAyuda()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.cerrar_sesion)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                onClick = {
                    abierto = false
                    confirmandoCierre = true
                },
            )
        }
    }
    // Confirmación: un toque por error obligaría a volver a ingresar (Nielsen H3 y H5)
    if (confirmandoCierre) {
        AlertDialog(
            onDismissRequest = { confirmandoCierre = false },
            title = { Text(stringResource(R.string.cerrar_sesion_pregunta)) },
            text = { Text(stringResource(R.string.cerrar_sesion_explicacion)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmandoCierre = false
                    acciones.cerrarSesion()
                }) { Text(stringResource(R.string.cerrar_sesion)) }
            },
            dismissButton = { TextButton(onClick = { confirmandoCierre = false }) { Text(stringResource(R.string.volver)) } },
        )
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
            dismissButton = { TextButton(onClick = { abierto = false }) { Text(stringResource(R.string.volver)) } },
        ) { DatePicker(estado) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private object DiasDeAtencion : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        HorarioAtencion.franja(Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()) != null
}

/**
 * Hora con el horario de atención del día a la vista ([franja], Nielsen H6) y sin poder elegir una
 * hora fuera de él (H5): el error se ve mientras se escribe, no recién al guardar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampoHora(
    etiqueta: String,
    hora: LocalTime,
    modifier: Modifier = Modifier,
    franja: Pair<LocalTime, LocalTime>? = null,
    alCambiar: (LocalTime) -> Unit,
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    Column(modifier) {
        BotonAncho("$etiqueta: ${formatearHora(hora)}", onClick = { abierto = true })
        franja?.let { (desde, hasta) ->
            Text(
                stringResource(R.string.horario_atencion_dia, formatearHora(desde), formatearHora(hasta)),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
    if (abierto) {
        val estado = rememberTimePickerState(initialHour = hora.hour, initialMinute = hora.minute, is24Hour = true)
        val elegida = LocalTime.of(estado.hour, estado.minute)
        // El inicio tiene que estar dentro del horario (y antes del cierre)
        val valida = franja == null || (!elegida.isBefore(franja.first) && elegida.isBefore(franja.second))
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(etiqueta) },
            text = {
                Column {
                    TimeInput(estado)
                    if (!valida && franja != null) {
                        TextoError(stringResource(R.string.horario_hora_fuera, formatearHora(franja.first), formatearHora(franja.second)))
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = valida, onClick = {
                    alCambiar(elegida)
                    abierto = false
                }) { Text(stringResource(R.string.aceptar)) }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text(stringResource(R.string.volver)) } },
        )
    }
}

/** Elegir paciente con búsqueda por nombre: con muchos pacientes no hay que recorrer la lista (Nielsen H6 y H7). */
@Composable
fun SelectorPaciente(
    pacientes: List<Paciente>,
    seleccionado: Paciente?,
    modifier: Modifier = Modifier,
    alElegir: (Paciente) -> Unit,
) {
    var abierto by rememberSaveable { mutableStateOf(false) }
    BotonAncho(
        stringResource(R.string.campo_paciente, seleccionado?.nombre ?: stringResource(R.string.elegir)),
        onClick = { abierto = true },
        modifier = modifier,
    )
    if (abierto) {
        var busqueda by rememberSaveable { mutableStateOf("") }
        val filtrados = pacientes.filter { it.nombre.contains(busqueda.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { abierto = false },
            title = { Text(stringResource(R.string.paciente_elegir_titulo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it },
                        placeholder = { Text(stringResource(R.string.pacientes_buscar)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (filtrados.isEmpty()) Text(stringResource(R.string.pacientes_sin_resultados))
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(filtrados, key = { it.id }) { paciente ->
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
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { abierto = false }) { Text(stringResource(R.string.volver)) } },
        )
    }
}

/**
 * Contraseña con botón para mostrarla u ocultarla (Nielsen H5: ver lo que se escribe evita errores).
 * Con [alEnviar], la tecla Enter del teclado envía el formulario.
 */
@Composable
fun CampoContrasenia(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier,
    alEnviar: (() -> Unit)? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = if (alEnviar != null) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { alEnviar?.invoke() }),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painterResource(if (visible) R.drawable.ic_ocultar else R.drawable.ic_mostrar),
                    contentDescription = stringResource(if (visible) R.string.ocultar_contrasenia else R.string.mostrar_contrasenia),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
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
