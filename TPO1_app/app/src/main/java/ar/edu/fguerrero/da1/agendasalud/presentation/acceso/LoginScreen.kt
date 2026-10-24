package ar.edu.fguerrero.da1.agendasalud.presentation.acceso

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.security.Autenticador
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TextoError
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.fabricaViewModel

/** Wireframe 01 · Acceso protegido. */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(factory = fabricaViewModel { LoginViewModel(it.credenciales, it.sesion) }),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as FragmentActivity
    val huellaDisponible = remember { Autenticador.disponible(activity) }

    var usuario by rememberSaveable { mutableStateOf("") }
    // Las contraseñas no se guardan en el estado restaurable
    var contrasenia by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.labelMedium)
        Text(stringResource(R.string.login_lema), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        if (estado.cargando) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            return@Column
        }

        Text(
            text = stringResource(if (estado.registro) R.string.login_crear_titulo else R.string.login_titulo),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterHorizontally).semantics { heading() },
        )
        if (estado.registro) {
            Text(stringResource(R.string.login_crear_descripcion), style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedTextField(
            value = usuario,
            onValueChange = { usuario = it },
            label = { Text(stringResource(R.string.login_usuario)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = contrasenia,
            onValueChange = { contrasenia = it },
            label = { Text(stringResource(R.string.login_contrasenia)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (estado.registro) {
            OutlinedTextField(
                value = confirmacion,
                onValueChange = { confirmacion = it },
                label = { Text(stringResource(R.string.login_confirmar)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        TextoError(estado.error?.let { stringResource(it) })

        Button(
            onClick = {
                if (estado.registro) viewModel.crearUsuario(usuario, contrasenia, confirmacion)
                else viewModel.ingresar(usuario, contrasenia)
            },
            enabled = !estado.procesando,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(if (estado.registro) R.string.login_crear else R.string.login_ingresar))
        }

        if (!estado.registro && huellaDisponible) {
            val tituloPrompt = stringResource(R.string.login_prompt_huella)
            TextButton(
                onClick = {
                    Autenticador.autenticar(
                        activity,
                        tituloPrompt,
                        alAutenticar = viewModel::ingresarConDispositivo,
                        alFallar = {},
                    )
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Filled.Fingerprint, contentDescription = null)
                Text(stringResource(R.string.login_con_huella), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
