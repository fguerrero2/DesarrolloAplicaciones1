package ar.edu.fguerrero.da1.agendasalud.presentation.pagos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaCorta
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearPesos
import ar.edu.fguerrero.da1.agendasalud.core.util.parsearPesos
import ar.edu.fguerrero.da1.agendasalud.core.util.pesosParaEditar
import ar.edu.fguerrero.da1.agendasalud.domain.model.CargoPendiente
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.MedioCobro
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.CampoFecha
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TarjetaTexto
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TextoError
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.mensaje
import java.time.LocalDate

/** Wireframe 13/14 · Registrar cobro (total o parcial) de una sesión completada. */
@Composable
fun RegistrarCobroDialog(
    cargo: CargoPendiente,
    alConfirmar: (montoCentavos: Long, fecha: LocalDate, medio: MedioCobro, alTerminar: (ErrorDominio?) -> Unit) -> Unit,
    alCerrar: () -> Unit,
) {
    var monto by remember { mutableStateOf(pesosParaEditar(cargo.pendienteCentavos)) }
    var fecha by remember { mutableStateOf(LocalDate.now()) }
    var medio by remember { mutableStateOf(MedioCobro.EFECTIVO) }
    var error by remember { mutableStateOf<Int?>(null) }
    var guardando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.cobro_titulo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TarjetaTexto(
                    listOf(
                        stringResource(R.string.cobro_sesion, cargo.nombrePaciente, formatearFechaCorta(cargo.fechaSesion)),
                        stringResource(R.string.cobro_pendiente_antes, formatearPesos(cargo.pendienteCentavos)),
                    )
                )
                OutlinedTextField(
                    value = monto,
                    onValueChange = { monto = it },
                    label = { Text(stringResource(R.string.campo_monto)) },
                    prefix = { Text("$ ") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                CampoFecha(stringResource(R.string.campo_fecha), fecha) { fecha = it }
                Text(stringResource(R.string.campo_medio))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = medio == MedioCobro.EFECTIVO,
                        onClick = { medio = MedioCobro.EFECTIVO },
                        label = { Text(stringResource(R.string.medio_efectivo)) },
                    )
                    FilterChip(
                        selected = medio == MedioCobro.MERCADO_PAGO,
                        onClick = { medio = MedioCobro.MERCADO_PAGO },
                        label = { Text(stringResource(R.string.medio_mercado_pago)) },
                    )
                }
                TextoError(error?.let { stringResource(it) })
            }
        },
        confirmButton = {
            TextButton(
                enabled = !guardando,
                onClick = {
                    val centavos = parsearPesos(monto)
                    if (centavos == null) {
                        error = R.string.error_monto_invalido
                    } else {
                        guardando = true
                        alConfirmar(centavos, fecha, medio) { e ->
                            guardando = false
                            error = e?.mensaje()
                        }
                    }
                },
            ) { Text(stringResource(R.string.cobro_confirmar)) }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}
