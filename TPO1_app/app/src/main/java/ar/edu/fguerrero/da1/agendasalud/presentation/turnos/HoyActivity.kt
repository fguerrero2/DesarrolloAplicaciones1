package ar.edu.fguerrero.da1.agendasalud.presentation.turnos

import androidx.compose.runtime.Composable
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.PantallaActivity
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.Pestania
import dagger.hilt.android.AndroidEntryPoint

/** Wireframe 02 · Hoy. Primera pantalla después del login. */
@AndroidEntryPoint
class HoyActivity : PantallaActivity() {
    override val pestania = Pestania.HOY

    @Composable
    override fun Contenido() = HoyScreen()
}
