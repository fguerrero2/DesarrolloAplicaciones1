package ar.edu.fguerrero.da1.agendasalud.presentation.sincronizacion

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.LocalSnackbar

/**
 * Informa en el snackbar cómo terminó "Sincronizar ahora".
 * - Éxito: mensaje breve que confirma que los datos están al día.
 * - Error: dice la causa y que los datos no se perdieron. Se muestra más tiempo para que se pueda
 *   leer, se puede cerrar y, si sirve de algo, ofrece "Reintentar" (control y libertad del usuario).
 */
@Composable
fun AvisosSincronizacion(viewModel: SincronizacionViewModel) {
    val snackbar = LocalSnackbar.current
    val recursos = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.resultados.collect { resultado ->
            val texto = recursos.getString(resultado.mensaje)
            when (resultado) {
                ResultadoSincronizacion.Exito -> snackbar.showSnackbar(texto, duration = SnackbarDuration.Short)
                is ResultadoSincronizacion.Fallo -> {
                    val accion = if (resultado.puedeReintentar) recursos.getString(R.string.sync_reintentar) else null
                    val respuesta = snackbar.showSnackbar(
                        message = texto,
                        actionLabel = accion,
                        // Más tiempo que un aviso común para leerlo, y con ✕ para cerrarlo antes
                        withDismissAction = true,
                        duration = SnackbarDuration.Long,
                    )
                    if (respuesta == SnackbarResult.ActionPerformed) viewModel.sincronizarAhora()
                }
            }
        }
    }
}
