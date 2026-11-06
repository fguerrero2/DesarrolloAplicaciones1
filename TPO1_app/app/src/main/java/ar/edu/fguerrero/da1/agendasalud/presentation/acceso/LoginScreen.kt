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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.security.Autenticador
import ar.edu.fguerrero.da1.agendasalud.presentation.AgendaSaludLogo
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TextoError

/** Wireframe 01 · Acceso protegido: ingreso, registro y recuperación de contraseña por email. */
@Composable
fun LoginScreen(modifier: Modifier = Modifier, viewModel: LoginViewModel = hiltViewModel()) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as FragmentActivity
    val huellaDisponible = remember { Autenticador.disponible(activity) }

    var email by rememberSaveable { mutableStateOf("") }
    // Las contraseñas no se guardan en el estado restaurable
    var contrasenia by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    val modo = estado.modo

    Box(
        modifier = modifier.fillMaxSize().safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // En tablet o en horizontal el formulario no se estira a todo el ancho
            modifier = Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AgendaSaludLogo(
                fontSize = 32.sp,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(24.dp))

            if (estado.cargando) {
                CircularProgressIndicator()
                return@Column
            }

            Text(
                text = stringResource(
                    when (modo) {
                        ModoAcceso.INGRESAR -> R.string.login_titulo
                        ModoAcceso.REGISTRARSE -> R.string.login_crear_titulo
                        ModoAcceso.RECUPERAR -> R.string.recuperar_titulo
                        ModoAcceso.NUEVA_CONTRASENIA -> R.string.restablecer_titulo
                    }
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            val explicacion = when (modo) {
                ModoAcceso.REGISTRARSE -> R.string.login_crear_descripcion
                ModoAcceso.RECUPERAR -> R.string.recuperar_descripcion
                // Sin la clave de datos en este celular, la copia del servidor deja de poder leerse
                ModoAcceso.NUEVA_CONTRASENIA -> if (estado.existeUsuario) R.string.restablecer_descripcion else R.string.restablecer_descripcion_sin_datos
                ModoAcceso.INGRESAR -> null
            }
            explicacion?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }

            if (modo != ModoAcceso.NUEVA_CONTRASENIA) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.login_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (modo != ModoAcceso.RECUPERAR) {
                OutlinedTextField(
                    value = contrasenia,
                    onValueChange = { contrasenia = it },
                    label = { Text(stringResource(if (modo == ModoAcceso.NUEVA_CONTRASENIA) R.string.restablecer_nueva else R.string.login_contrasenia)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (modo == ModoAcceso.REGISTRARSE || modo == ModoAcceso.NUEVA_CONTRASENIA) {
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

            estado.aviso?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            TextoError(estado.error?.let { stringResource(it) } ?: estado.errorDispositivo)

            Button(
                onClick = {
                    when (modo) {
                        ModoAcceso.INGRESAR -> viewModel.ingresar(email, contrasenia)
                        ModoAcceso.REGISTRARSE -> viewModel.crearUsuario(email, contrasenia, confirmacion)
                        ModoAcceso.RECUPERAR -> viewModel.pedirRecuperacion(email)
                        ModoAcceso.NUEVA_CONTRASENIA -> viewModel.restablecer(contrasenia, confirmacion)
                    }
                },
                enabled = !estado.procesando,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                if (estado.procesando) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        stringResource(
                            when (modo) {
                                ModoAcceso.INGRESAR -> R.string.login_ingresar
                                ModoAcceso.REGISTRARSE -> R.string.login_crear
                                ModoAcceso.RECUPERAR -> R.string.recuperar_enviar
                                ModoAcceso.NUEVA_CONTRASENIA -> R.string.restablecer_guardar
                            }
                        )
                    )
                }
            }

            if (modo != ModoAcceso.INGRESAR) {
                TextButton(
                    onClick = { viewModel.cambiarModo(ModoAcceso.INGRESAR) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.volver)) }
                return@Column
            }

            TextButton(
                onClick = { viewModel.cambiarModo(ModoAcceso.RECUPERAR) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.recuperar_link)) }

            if (!estado.existeUsuario) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.login_sin_usuario), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                OutlinedButton(
                    onClick = { viewModel.cambiarModo(ModoAcceso.REGISTRARSE) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.login_registrarse)) }
            } else if (huellaDisponible) {
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
