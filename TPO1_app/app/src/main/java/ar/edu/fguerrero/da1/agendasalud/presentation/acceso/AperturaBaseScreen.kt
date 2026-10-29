package ar.edu.fguerrero.da1.agendasalud.presentation.acceso

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ar.edu.fguerrero.da1.agendasalud.R

/** Mientras se abre la base cifrada (normalmente es instantáneo). */
@Composable
fun AbriendoBaseScreen() {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.base_abriendo))
        }
    }
}

/**
 * La base no se pudo abrir (por ejemplo, Android invalidó la clave del Keystore).
 * Se ofrece reintentar y, como último recurso, empezar de cero con confirmación explícita.
 */
@Composable
fun ErrorBaseScreen(alReintentar: () -> Unit, alEmpezarDeCero: () -> Unit, alSalir: () -> Unit) {
    var confirmando by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.base_error_titulo), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(stringResource(R.string.base_error_descripcion), textAlign = TextAlign.Center)
            Button(onClick = alReintentar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.base_reintentar))
            }
            OutlinedButton(onClick = { confirmando = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.base_empezar_de_cero))
            }
            TextButton(onClick = alSalir, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.cerrar_sesion))
            }
        }
    }

    if (confirmando) {
        AlertDialog(
            onDismissRequest = { confirmando = false },
            title = { Text(stringResource(R.string.base_empezar_de_cero)) },
            text = { Text(stringResource(R.string.base_empezar_de_cero_explicacion)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmando = false
                    alEmpezarDeCero()
                }) { Text(stringResource(R.string.base_borrar_todo), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmando = false }) { Text(stringResource(R.string.volver)) } },
        )
    }
}
