package ar.edu.fguerrero.da1.agendasalud.presentation.pagos

import androidx.compose.runtime.Composable
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.PantallaActivity
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.Pestania
import dagger.hilt.android.AndroidEntryPoint

/** Wireframe 12 · Cobros pendientes y registro de cobros. */
@AndroidEntryPoint
class CobrosActivity : PantallaActivity() {
    override val pestania = Pestania.COBROS

    @Composable
    override fun Contenido() = CobrosScreen()
}
