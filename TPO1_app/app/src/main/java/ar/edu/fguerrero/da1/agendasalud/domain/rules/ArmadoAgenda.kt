package ar.edu.fguerrero.da1.agendasalud.domain.rules

import ar.edu.fguerrero.da1.agendasalud.domain.model.BloqueoAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.EstadoTurno
import ar.edu.fguerrero.da1.agendasalud.domain.model.Intervalo
import ar.edu.fguerrero.da1.agendasalud.domain.model.ItemAgenda
import ar.edu.fguerrero.da1.agendasalud.domain.model.TurnoConPaciente
import java.time.LocalDate

/** Arma la agenda de un día mezclando turnos, bloqueos y huecos libres dentro del horario de atención. */
object ArmadoAgenda {

    fun armar(
        fecha: LocalDate,
        turnos: List<TurnoConPaciente>,
        bloqueos: List<BloqueoAgenda>,
        minutosHueco: Int = 60,
    ): List<ItemAgenda> {
        val ocupados = turnos.filter { it.turno.estado != EstadoTurno.CANCELADO }.map { it.turno.intervalo } +
            bloqueos.map { it.intervalo }

        val libres = mutableListOf<ItemAgenda.Libre>()
        HorarioAtencion.franja(fecha)?.let { (apertura, cierre) ->
            var desde = fecha.atTime(apertura)
            val fin = fecha.atTime(cierre)
            while (!desde.plusMinutes(minutosHueco.toLong()).isAfter(fin)) {
                val hueco = Intervalo(desde, desde.plusMinutes(minutosHueco.toLong()))
                if (ocupados.none { it.seSuperponeCon(hueco) }) libres += ItemAgenda.Libre(hueco.inicio, hueco.fin)
                desde = hueco.fin
            }
        }

        return (turnos.map { ItemAgenda.Ocupado(it) } + bloqueos.map { ItemAgenda.Bloqueado(it) } + libres)
            .sortedBy { it.inicio }
    }
}
