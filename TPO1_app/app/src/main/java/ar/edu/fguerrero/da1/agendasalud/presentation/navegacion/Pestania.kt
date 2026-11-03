package ar.edu.fguerrero.da1.agendasalud.presentation.navegacion

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import ar.edu.fguerrero.da1.agendasalud.R
import ar.edu.fguerrero.da1.agendasalud.presentation.pacientes.PacientesActivity
import ar.edu.fguerrero.da1.agendasalud.presentation.pagos.CobrosActivity
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.AgendaActivity
import ar.edu.fguerrero.da1.agendasalud.presentation.turnos.HoyActivity
import kotlin.reflect.KClass

/** Las 4 pantallas de la barra de navegación. Cada una es una Activity distinta. */
enum class Pestania(val activity: KClass<out Activity>, @param:StringRes val etiqueta: Int, @param:DrawableRes val icono: Int) {
    HOY(HoyActivity::class, R.string.nav_hoy, R.drawable.ic_hoy),
    AGENDA(AgendaActivity::class, R.string.nav_agenda, R.drawable.ic_agenda),
    PACIENTES(PacientesActivity::class, R.string.nav_pacientes, R.drawable.ic_pacientes),
    COBROS(CobrosActivity::class, R.string.nav_cobros, R.drawable.ic_cobros),
}
