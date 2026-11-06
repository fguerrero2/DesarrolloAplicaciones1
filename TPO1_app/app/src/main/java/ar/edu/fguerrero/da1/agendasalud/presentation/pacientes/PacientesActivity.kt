package ar.edu.fguerrero.da1.agendasalud.presentation.pacientes

import androidx.compose.runtime.Composable
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.PantallaActivity
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.Pestania
import dagger.hilt.android.AndroidEntryPoint

/** Wireframe 04 · Pacientes: lista, ficha, alta y edición. */
@AndroidEntryPoint
class PacientesActivity : PantallaActivity() {
    override val pestania = Pestania.PACIENTES

    @Composable
    override fun Contenido() = PacientesScreen()
}
