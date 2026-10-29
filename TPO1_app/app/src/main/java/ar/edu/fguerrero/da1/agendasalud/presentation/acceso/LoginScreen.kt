package ar.edu.fguerrero.da1.agendasalud.presentation.acceso

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.security.Autenticador
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TextoError
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.fabricaViewModel

/** Wireframe 01 · Acceso protegido: ingreso y, si todavía no hay usuario, registro. */
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

    Box(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            Spacer(Modifier.height(24.dp))

            if (estado.cargando) {
                CircularProgressIndicator()
                return@Column
            }

            Text(
                text = stringResource(if (estado.registrando) R.string.login_crear_titulo else R.string.login_titulo),
                style = MaterialTheme.typography.titleLarge,
            )
            if (estado.registrando) {
                Text(
                    stringResource(R.string.login_crear_descripcion),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
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
            if (estado.registrando) {
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

            TextoError(estado.error?.let { stringResource(it) } ?: estado.errorDispositivo)

            Button(
                onClick = {
                    if (estado.registrando) viewModel.crearUsuario(usuario, contrasenia, confirmacion)
                    else viewModel.ingresar(usuario, contrasenia)
                },
                enabled = !estado.procesando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(if (estado.registrando) R.string.login_crear else R.string.login_ingresar))
            }

            when {
                estado.registrando -> TextButton(
                    onClick = { viewModel.mostrarRegistro(false) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.volver)) }

                !estado.existeUsuario -> {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.login_sin_usuario), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { viewModel.mostrarRegistro(true) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.login_registrarse)) }
                }

                huellaDisponible -> {
                    val tituloPrompt = stringResource(R.string.login_prompt_huella)
                    TextButton(
                        onClick = {
                            Autenticador.autenticar(
                                activity,
                                tituloPrompt,
                                alAutenticar = viewModel::ingresarConDispositivo,
                                alFallar = viewModel::falloDispositivo,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Text(stringResource(R.string.login_con_huella), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
