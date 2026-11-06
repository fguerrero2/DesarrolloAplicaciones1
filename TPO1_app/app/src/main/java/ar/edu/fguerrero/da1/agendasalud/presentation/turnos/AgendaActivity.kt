package ar.edu.fguerrero.da1.agendasalud.presentation.turnos

import androidx.compose.runtime.Composable
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.PantallaActivity
import ar.edu.fguerrero.da1.agendasalud.presentation.navegacion.Pestania
import dagger.hilt.android.AndroidEntryPoint

/** Wireframe 03 · Agenda del día, con selector de fecha y bloqueo de períodos. */
@AndroidEntryPoint
class AgendaActivity : PantallaActivity() {
    override val pestania = Pestania.AGENDA

    @Composable
    override fun Contenido() = AgendaScreen()
}
