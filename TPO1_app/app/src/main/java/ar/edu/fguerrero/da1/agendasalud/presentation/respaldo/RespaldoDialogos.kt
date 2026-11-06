package ar.edu.fguerrero.da1.agendasalud.presentation.respaldo

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.MostrarAvisos
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TextoError
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.mensaje

/**
 * Diálogos y selectores de archivo del respaldo. Se muestran sobre cualquiera de las 4 pantallas,
 * porque se abren desde el menú ⋮ del encabezado.
 *
 * Los archivos se eligen con el Storage Access Framework (CreateDocument / OpenDocument): el usuario
 * decide dónde guardar (Drive, Descargas, un pendrive) y la app no necesita permisos de almacenamiento.
 */
@Composable
fun RespaldoDialogos(viewModel: RespaldoViewModel) {
    val paso by viewModel.paso.collectAsStateWithLifecycle()
    MostrarAvisos(viewModel.avisos)

    val guardarComo = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TIPO_ARCHIVO)) {
        viewModel.destinoElegido(it?.toString())
    }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.origenElegido(it?.toString())
    }
    LaunchedEffect(viewModel) {
        viewModel.selectorArchivo.collect { pedido ->
            try {
                when (pedido) {
                    is SelectorArchivo.Guardar -> guardarComo.launch(pedido.nombreSugerido)
                    // Algunos proveedores no reconocen la extensión .respaldo: se muestran todos los archivos
                    SelectorArchivo.Abrir -> abrir.launch(arrayOf("*/*"))
                }
            } catch (e: ActivityNotFoundException) {
                // Celular sin selector de archivos (muy raro): se trata como si hubiera cancelado
                viewModel.destinoElegido(null)
            }
        }
    }

    when (val actual = paso) {
        PasoRespaldo.Inactivo -> Unit
        PasoRespaldo.ClaveParaExportar -> ClaveExportarDialog(
            alConfirmar = viewModel::elegirClaveExportar,
            alCerrar = viewModel::cancelar,
        )
        PasoRespaldo.ConfirmarRestaurar -> AlertDialog(
            onDismissRequest = viewModel::cancelar,
            title = { Text(stringResource(R.string.respaldo_restaurar)) },
            text = { Text(stringResource(R.string.respaldo_restaurar_advertencia)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmarRestaurar) { Text(stringResource(R.string.respaldo_elegir_archivo)) }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelar) { Text(stringResource(R.string.cancelar)) } },
        )
        is PasoRespaldo.ClaveParaRestaurar -> ClaveRestaurarDialog(
            alConfirmar = viewModel::restaurar,
            alCerrar = viewModel::cancelar,
        )
        PasoRespaldo.Procesando -> AlertDialog(
            // No se puede cerrar mientras se escribe el archivo
            onDismissRequest = {},
            title = { Text(stringResource(R.string.respaldo_procesando)) },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            },
            confirmButton = {},
        )
    }
}

/** Paso 1 de exportar: elegir la contraseña del respaldo (dos veces, para no equivocarse). */
@Composable
private fun ClaveExportarDialog(alConfirmar: (clave: String, confirmacion: String) -> Int?, alCerrar: () -> Unit) {
    // Las contraseñas usan remember y no rememberSaveable: no deben guardarse en el estado restaurable
    var clave by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(R.string.respaldo_exportar)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.respaldo_exportar_explicacion), style = MaterialTheme.typography.bodyMedium)
                CampoClave(clave, { clave = it }, stringResource(R.string.respaldo_clave))
                CampoClave(confirmacion, { confirmacion = it }, stringResource(R.string.respaldo_clave_confirmar))
                TextoError(error?.let { stringResource(it) })
            }
        },
        confirmButton = {
            TextButton(onClick = { error = alConfirmar(clave, confirmacion) }) {
                Text(stringResource(R.string.respaldo_elegir_destino))
            }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

/** Último paso de restaurar: la contraseña con la que se exportó. */
@Composable
private fun ClaveRestaurarDialog(alConfirmar: (clave: String, alTerminar: (ErrorDominio?) -> Unit) -> Unit, alCerrar: () -> Unit) {
    var clave by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var restaurando by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!restaurando) alCerrar() },
        title = { Text(stringResource(R.string.respaldo_restaurar)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.respaldo_restaurar_clave_explicacion), style = MaterialTheme.typography.bodyMedium)
                CampoClave(clave, { clave = it }, stringResource(R.string.respaldo_clave))
                TextoError(error?.let { stringResource(it) })
                if (restaurando) CircularProgressIndicator()
            }
        },
        confirmButton = {
            TextButton(enabled = !restaurando && clave.isNotEmpty(), onClick = {
                restaurando = true
                error = null
                alConfirmar(clave) { e ->
                    restaurando = false
                    error = e?.mensaje()
                }
            }) { Text(stringResource(R.string.respaldo_restaurar_confirmar)) }
        },
        dismissButton = { TextButton(enabled = !restaurando, onClick = alCerrar) { Text(stringResource(R.string.cancelar)) } },
    )
}

@Composable
private fun CampoClave(valor: String, alCambiar: (String) -> Unit, etiqueta: String) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val TIPO_ARCHIVO = "application/octet-stream"
