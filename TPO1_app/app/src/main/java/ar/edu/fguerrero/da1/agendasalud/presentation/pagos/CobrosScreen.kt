package ar.edu.fguerrero.da1.agendasalud.presentation.pagos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearFechaCorta
import ar.edu.fguerrero.da1.agendasalud.core.util.formatearPesos
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.BotonAncho
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.Encabezado
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.MostrarAvisos
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.TarjetaTexto
import ar.edu.fguerrero.da1.agendasalud.presentation.comun.fabricaViewModel

/** Wireframe 12 · Cobros pendientes. El cobro se registra desde acá mismo. */
@Composable
fun CobrosScreen(
    viewModel: CobrosViewModel = viewModel(factory = fabricaViewModel { CobrosViewModel(it.cobros, it.registrarCobro) }),
) {
    val estado by viewModel.uiState.collectAsStateWithLifecycle()
    val cobro by viewModel.cobroEnCurso.collectAsStateWithLifecycle()
    MostrarAvisos(viewModel.avisos)

    Column(Modifier.fillMaxSize()) {
        Encabezado(
            stringResource(R.string.cobros_titulo),
            stringResource(R.string.cobros_total, formatearPesos(estado.totalPendienteCentavos)),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = estado.soloPendientes,
                onClick = { viewModel.filtrarPendientes(true) },
                label = { Text(stringResource(R.string.cobros_filtro_pendientes)) },
            )
            FilterChip(
                selected = !estado.soloPendientes,
                onClick = { viewModel.filtrarPendientes(false) },
                label = { Text(stringResource(R.string.cobros_filtro_todos)) },
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (estado.cargando) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (estado.cargos.isEmpty()) {
                item { Text(stringResource(R.string.cobros_vacio)) }
            }
            items(estado.cargos, key = { it.cargoId }) { cargo ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TarjetaTexto(
                        listOf(
                            stringResource(R.string.cobro_sesion, cargo.nombrePaciente, formatearFechaCorta(cargo.fechaSesion)),
                            stringResource(R.string.cobros_cargo_cobrado, formatearPesos(cargo.importeCentavos), formatearPesos(cargo.cobradoCentavos)),
                            if (cargo.pendienteCentavos > 0) stringResource(R.string.cobros_pendiente, formatearPesos(cargo.pendienteCentavos))
                            else stringResource(R.string.cobros_saldado),
                        )
                    )
                    if (cargo.pendienteCentavos > 0) {
                        BotonAncho(stringResource(R.string.turno_registrar_cobro), onClick = { viewModel.abrirCobro(cargo) })
                    }
                }
            }
            item {
                Text(stringResource(R.string.cobros_nota), style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    cobro?.let { cargo ->
        RegistrarCobroDialog(cargo = cargo, alConfirmar = viewModel::registrarCobro, alCerrar = viewModel::cerrarCobro)
    }
}
