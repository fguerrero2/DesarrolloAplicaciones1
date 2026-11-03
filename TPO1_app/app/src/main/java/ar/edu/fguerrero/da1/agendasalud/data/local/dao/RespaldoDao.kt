package ar.edu.fguerrero.da1.agendasalud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.BloqueoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CambioTurnoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CargoSesionEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CobroEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.PacienteEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.TurnoEntity

/**
 * Lectura y reemplazo completo de la base para el respaldo. Siempre se usa dentro de una
 * transacción (ver RespaldoRepositoryImpl), así el respaldo es una foto consistente y la
 * restauración es todo o nada.
 */
@Dao
interface RespaldoDao {
    @Query("SELECT * FROM pacientes") suspend fun pacientes(): List<PacienteEntity>
    @Query("SELECT * FROM turnos") suspend fun turnos(): List<TurnoEntity>
    @Query("SELECT * FROM bloqueos") suspend fun bloqueos(): List<BloqueoEntity>
    @Query("SELECT * FROM cargos_sesion") suspend fun cargos(): List<CargoSesionEntity>
    @Query("SELECT * FROM cobros") suspend fun cobros(): List<CobroEntity>
    @Query("SELECT * FROM cambios_turno") suspend fun cambios(): List<CambioTurnoEntity>

    // Se insertan con su id original para que las claves foráneas sigan apuntando bien
    @Insert suspend fun insertarPacientes(filas: List<PacienteEntity>)
    @Insert suspend fun insertarTurnos(filas: List<TurnoEntity>)
    @Insert suspend fun insertarBloqueos(filas: List<BloqueoEntity>)
    @Insert suspend fun insertarCargos(filas: List<CargoSesionEntity>)
    @Insert suspend fun insertarCobros(filas: List<CobroEntity>)
    @Insert suspend fun insertarCambios(filas: List<CambioTurnoEntity>)

    /** Borra en orden inverso a las claves foráneas (primero lo que depende de otra tabla). */
    suspend fun borrarTodo() {
        borrarCobros()
        borrarCargos()
        borrarCambios()
        borrarTurnos()
        borrarBloqueos()
        borrarPacientes()
    }

    @Query("DELETE FROM cobros") suspend fun borrarCobros()
    @Query("DELETE FROM cargos_sesion") suspend fun borrarCargos()
    @Query("DELETE FROM cambios_turno") suspend fun borrarCambios()
    @Query("DELETE FROM turnos") suspend fun borrarTurnos()
    @Query("DELETE FROM bloqueos") suspend fun borrarBloqueos()
    @Query("DELETE FROM pacientes") suspend fun borrarPacientes()
}
