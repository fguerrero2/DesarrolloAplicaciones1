package ar.edu.fguerrero.da1.agendasalud.presentation.ayuda

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.domain.repository.PreferenciasRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Qué ayuda se muestra: la guía de bienvenida (una sola vez) o la ayuda pedida desde el menú. */
enum class ModoAyuda { GUIA, AYUDA }

/**
 * Ayuda y documentación (Nielsen H10). La primera vez que se entra se muestra una guía corta; después
 * queda disponible en ⋮ → ¿Cómo funciona? El contenido es el mismo, breve y orientado a tareas.
 */
@HiltViewModel
class AyudaViewModel @Inject constructor(
    private val preferencias: PreferenciasRepository,
) : ViewModel() {

    private val ayudaPedida = MutableStateFlow(false)

    /** null mientras no hay que mostrar nada. Hasta leer la preferencia, no se muestra la guía. */
    val modo: StateFlow<ModoAyuda?> = combine(preferencias.guiaVista, ayudaPedida) { guiaVista, pedida ->
        when {
            pedida -> ModoAyuda.AYUDA
            !guiaVista -> ModoAyuda.GUIA
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun abrir() {
        ayudaPedida.value = true
    }

    fun cerrar() {
        ayudaPedida.value = false
        viewModelScope.launch { preferencias.marcarGuiaVista() }
    }
}

private val SECCIONES: List<Pair<Int, Int>> = listOf(
    R.string.ayuda_pasos_titulo to R.string.ayuda_pasos,
    R.string.ayuda_turnos_titulo to R.string.ayuda_turnos,
    R.string.ayuda_cobros_titulo to R.string.ayuda_cobros,
    R.string.ayuda_inactivos_titulo to R.string.ayuda_inactivos,
    R.string.ayuda_sync_titulo to R.string.ayuda_sync,
    R.string.ayuda_respaldo_titulo to R.string.ayuda_respaldo,
    R.string.ayuda_contrasenia_titulo to R.string.ayuda_contrasenia,
)

@Composable
fun AyudaDialog(modo: ModoAyuda, alCerrar: () -> Unit) {
    val esGuia = modo == ModoAyuda.GUIA
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text(stringResource(if (esGuia) R.string.guia_titulo else R.string.ayuda_titulo)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (esGuia) Text(stringResource(R.string.guia_introduccion), style = MaterialTheme.typography.bodyMedium)
                // En la guía alcanza con lo esencial; la ayuda completa está en el menú
                (if (esGuia) SECCIONES.take(3) else SECCIONES).forEach { (titulo, texto) -> Seccion(titulo, texto) }
            }
        },
        confirmButton = {
            TextButton(onClick = alCerrar) { Text(stringResource(if (esGuia) R.string.guia_empezar else R.string.cerrar)) }
        },
    )
}

@Composable
private fun Seccion(@StringRes titulo: Int, @StringRes texto: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(titulo), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(texto), style = MaterialTheme.typography.bodyMedium)
    }
}
