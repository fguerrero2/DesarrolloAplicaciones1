package ar.edu.fguerrero.da1.agendasalud.presentation.disponibilidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.CampoFecha
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.CampoHora
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TextoError
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.mensaje
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Wireframe 22 · Bloquear período (vacaciones, cursos, eventos). */
@Composable
fun BloquearPeriodoDialog(
    fechaInicial: LocalDate,
    alGuardar: (inicio: LocalDateTime, fin: LocalDateTime, motivo: String, alTerminar: (ErrorDominio?) -> Unit) -> Unit,
    alCerrar: () -> Unit,
) {
    // rememberSaveable: lo cargado no se pierde al girar la pantalla
    var desdeFecha by rememberSaveable { mutableStateOf(fechaInicial) }
    var desdeHora by rememberSaveable { mutableStateOf(LocalTime.of(9, 0)) }
    var hastaFecha by rememberSaveable { mutableStateOf(fechaInicial) }
    var hastaHora by rememberSaveable { mutableStateOf(LocalTime.of(18, 0)) }
    var motivo by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var guardando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.bloqueo_titulo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoFecha(stringResource(R.string.campo_desde), desdeFecha) {
                    desdeFecha = it
                    if (hastaFecha.isBefore(it)) hastaFecha = it
                }
                CampoHora(stringResource(R.string.campo_hora), desdeHora) { desdeHora = it }
                CampoFecha(stringResource(R.string.campo_hasta), hastaFecha) { hastaFecha = it }
                CampoHora(stringResource(R.string.campo_hora), hastaHora) { hastaHora = it }
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { motivo = it },
                    label = { Text(stringResource(R.string.campo_motivo)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextoError(error?.let { stringResource(it) })
            }
        },
        confirmButton = {
            TextButton(enabled = !guardando, onClick = {
                if (motivo.isBlank()) {
                    error = R.string.error_motivo_vacio
                    return@TextButton
                }
                guardando = true
                alGuardar(desdeFecha.atTime(desdeHora), hastaFecha.atTime(hastaHora), motivo) { e ->
                    guardando = false
                    error = e?.mensaje()
                }
            }) { Text(stringResource(R.string.bloqueo_guardar)) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}
