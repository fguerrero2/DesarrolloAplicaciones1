package ar.edu.fguerrero.da1.agendasalud.presentation.turnos

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaNumerica
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearHora
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearPesos
import ar.edu.fguerrero.da1.agendasalud.core.util.parsearPesos
import ar.edu.fguerrero.da1.agendasalud.core.util.pesosParaEditar
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.Paciente
import ar.edu.fguerrero.da1.agendasalud.domain.model.TurnoConPaciente
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.BotonAncho
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.CampoFecha
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.CampoHora
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.SelectorPaciente
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TarjetaTexto
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TextoError
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.mensaje
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Wireframe 08 · Nuevo turno. */
@Composable
fun NuevoTurnoDialog(
    pacientes: List<Paciente>,
    fechaInicial: LocalDate,
    horaInicial: LocalTime,
    duracionInicial: Int,
    importeInicial: Long,
    alGuardar: (pacienteId: Long, inicio: LocalDateTime, duracion: Int, importe: Long, alTerminar: (ErrorDominio?) -> Unit) -> Unit,
    alCerrar: () -> Unit,
) {
    var paciente by remember { mutableStateOf(pacientes.singleOrNull()) }
    var fecha by remember { mutableStateOf(fechaInicial) }
    var hora by remember { mutableStateOf(horaInicial) }
    var duracion by remember { mutableStateOf(duracionInicial.toString()) }
    var importe by remember { mutableStateOf(if (importeInicial > 0) pesosParaEditar(importeInicial) else "") }
    var error by remember { mutableStateOf<Int?>(null) }
    var guardando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.turno_nuevo_titulo)) },
        text = {
            if (pacientes.isEmpty()) {
                Text(stringResource(R.string.turno_sin_pacientes))
                return@AlertDialog
            }
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorPaciente(pacientes, paciente) { paciente = it }
                CampoFecha(stringResource(R.string.campo_fecha), fecha, soloDiasDeAtencion = true) { fecha = it }
                CampoHora(stringResource(R.string.campo_hora), hora) { hora = it }
                OutlinedTextField(
                    value = duracion,
                    onValueChange = { duracion = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.campo_duracion)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = importe,
                    onValueChange = { importe = it },
                    label = { Text(stringResource(R.string.campo_importe)) },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextoError(error?.let { stringResource(it) })
            }
        },
        confirmButton = {
            if (pacientes.isNotEmpty()) {
                TextButton(
                    enabled = !guardando,
                    onClick = {
                        val elegido = paciente
                        val minutos = duracion.toIntOrNull()
                        val centavos = parsearPesos(importe)
                        error = when {
                            elegido == null -> R.string.error_elegir_paciente
                            minutos == null || minutos <= 0 -> R.string.error_duracion_invalida
                            centavos == null || centavos < 0 -> R.string.error_monto_invalido
                            else -> null
                        }
                        if (error == null) {
                            guardando = true
                            alGuardar(elegido!!.id, fecha.atTime(hora), minutos!!, centavos!!) { e ->
                                guardando = false
                                error = e?.mensaje()
                            }
                        }
                    },
                ) { Text(stringResource(R.string.turno_guardar)) }
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** Wireframe 10 · Detalle del turno, como panel inferior con las acciones disponibles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccionesTurnoSheet(
    item: TurnoConPaciente,
    alCompletar: () -> Unit,
    alRegistrarCobro: () -> Unit,
    alReprogramar: () -> Unit,
    alCancelar: () -> Unit,
    alCerrar: () -> Unit,
) {
    val turno = item.turno
    ModalBottomSheet(onDismissRequest = alCerrar) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(item.nombrePaciente, style = MaterialTheme.typography.headlineSmall)
            TarjetaTexto(
                listOfNotNull(
                    "${formatearFechaNumerica(turno.inicio)} · ${formatearHora(turno.inicio)}–${formatearHora(turno.fin)}",
                    "${stringResource(etiquetaEstado(turno.estado))} · ${stringResource(R.string.turno_importe, formatearPesos(turno.importeCentavos))}",
                    turno.fechaOriginal?.let { stringResource(R.string.turno_original, formatearFechaNumerica(it), formatearHora(it)) },
                    turno.motivoCancelacion?.let { stringResource(R.string.turno_motivo, it) },
                )
            )
            when (turno.estado) {
                EstadoTurno.PROGRAMADO -> {
                    Text(stringResource(R.string.turno_cobro_al_completar), style = MaterialTheme.typography.bodyMedium)
                    BotonAncho(stringResource(R.string.turno_completar), onClick = alCompletar)
                    BotonAncho(stringResource(R.string.turno_reprogramar), onClick = alReprogramar)
                    BotonAncho(stringResource(R.string.turno_cancelar), onClick = alCancelar)
                }
                EstadoTurno.COMPLETADO -> BotonAncho(stringResource(R.string.turno_registrar_cobro), onClick = alRegistrarCobro)
                EstadoTurno.CANCELADO -> Unit
            }
        }
    }
}

/** Wireframe 17 · Reprogramar turno. */
@Composable
fun ReprogramarDialog(
    item: TurnoConPaciente,
    alGuardar: (LocalDateTime, alTerminar: (ErrorDominio?) -> Unit) -> Unit,
    alCerrar: () -> Unit,
) {
    var fecha by remember { mutableStateOf(item.turno.inicio.toLocalDate()) }
    var hora by remember { mutableStateOf(item.turno.inicio.toLocalTime()) }
    var error by remember { mutableStateOf<Int?>(null) }
    var guardando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.turno_reprogramar)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.turno_reprogramar_actual, item.nombrePaciente, formatearFechaNumerica(item.turno.inicio), formatearHora(item.turno.inicio)))
                CampoFecha(stringResource(R.string.campo_nueva_fecha), fecha, soloDiasDeAtencion = true) { fecha = it }
                CampoHora(stringResource(R.string.campo_nueva_hora), hora) { hora = it }
                TextoError(error?.let { stringResource(it) })
            }
        },
        confirmButton = {
            TextButton(enabled = !guardando, onClick = {
                guardando = true
                alGuardar(fecha.atTime(hora)) { e ->
                    guardando = false
                    error = e?.mensaje()
                }
            }) { Text(stringResource(R.string.guardar_cambio)) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** Wireframe 20 · Cancelar turno. */
@Composable
fun CancelarTurnoDialog(item: TurnoConPaciente, alConfirmar: (motivo: String) -> Unit, alCerrar: () -> Unit) {
    var motivo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.turno_cancelar)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.turno_cancelar_pregunta, item.nombrePaciente, formatearHora(item.turno.inicio)))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text(stringResource(R.string.campo_motivo_opcional)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { alConfirmar(motivo) }) { Text(stringResource(R.string.turno_cancelar_confirmar)) } },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.volver)) } },
    )
}

/**
 * Wireframe 19 · Borrador de WhatsApp (RF05). Abre WhatsApp con el texto para que el profesional lo
 * revise y lo envíe; nunca se envía automáticamente. Si no se puede abrir, se ofrece copiar el texto.
 */
@Composable
fun WhatsAppDialog(borrador: BorradorWhatsApp, alCerrar: () -> Unit) {
    val context = LocalContext.current
    var texto by remember { mutableStateOf(borrador.texto) }
    var aviso by remember { mutableStateOf<Int?>(null) }
    val numero = borrador.telefono?.let(::numeroWhatsApp)

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.whatsapp_titulo)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = texto, onValueChange = { texto = it }, modifier = Modifier.fillMaxWidth())
                if (numero == null) Text(stringResource(R.string.whatsapp_sin_telefono))
                aviso?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    copiar(context, texto)
                    aviso = R.string.whatsapp_copiado
                }) { Text(stringResource(R.string.whatsapp_copiar)) }
                if (numero != null) {
                    TextButton(onClick = {
                        if (abrirWhatsApp(context, numero, texto)) alCerrar()
                        else {
                            copiar(context, texto)
                            aviso = R.string.whatsapp_no_disponible
                        }
                    }) { Text(stringResource(R.string.whatsapp_abrir)) }
                }
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.ahora_no)) } },
    )
}

/** Normaliza un teléfono argentino a formato internacional para wa.me (54 9 + área + número). */
internal fun numeroWhatsApp(telefono: String): String? {
    val digitos = telefono.filter(Char::isDigit).removePrefix("00")
    return when {
        digitos.length < 8 -> null
        digitos.startsWith("54") -> digitos
        digitos.length == 10 -> "549$digitos"
        else -> digitos
    }
}

private fun abrirWhatsApp(context: Context, numero: String, texto: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, "https://wa.me/$numero?text=${Uri.encode(texto)}".toUri()))
    true
} catch (_: ActivityNotFoundException) {
    false
}

private fun copiar(context: Context, texto: String) {
    val portapapeles = context.getSystemService(ClipboardManager::class.java)
    portapapeles.setPrimaryClip(ClipData.newPlainText("AgendaSalud", texto))
}

fun etiquetaEstado(estado: EstadoTurno) = when (estado) {
    EstadoTurno.PROGRAMADO -> R.string.estado_programado
    EstadoTurno.COMPLETADO -> R.string.estado_completado
    EstadoTurno.CANCELADO -> R.string.estado_cancelado
}
