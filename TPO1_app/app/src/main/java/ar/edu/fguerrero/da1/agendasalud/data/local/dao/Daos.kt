package ar.edu.fguerrero.da1.agendasalud.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.BloqueoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CambioTurnoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CargoConCobrado
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CargoSesionEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CobroEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.PacienteEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.SaldoPacienteFila
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.TurnoConNombre
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.TurnoEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface PacienteDao {
    @Query(
        """
        SELECT * FROM pacientes
        WHERE (:incluirInactivos OR activo = 1) AND nombre LIKE '%' || :busqueda || '%'
        ORDER BY nombre COLLATE NOCASE
        """
    )
    fun observar(busqueda: String, incluirInactivos: Boolean): Flow<List<PacienteEntity>>

    @Query("SELECT * FROM pacientes WHERE id = :id")
    suspend fun obtener(id: Long): PacienteEntity?

    @Insert
    suspend fun insertar(paciente: PacienteEntity): Long

    @Update
    suspend fun actualizar(paciente: PacienteEntity): Int

    @Query("UPDATE pacientes SET activo = 0 WHERE id = :id")
    suspend fun desactivar(id: Long): Int
}

@Dao
interface TurnoDao {
    @Query(
        """
        SELECT t.*, p.nombre AS nombrePaciente FROM turnos t
        JOIN pacientes p ON p.id = t.pacienteId
        WHERE t.inicio >= :desde AND t.inicio < :hasta
        ORDER BY t.inicio
        """
    )
    fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<TurnoConNombre>>

    @Query("SELECT * FROM turnos WHERE pacienteId = :pacienteId ORDER BY inicio DESC")
    fun observarDePaciente(pacienteId: Long): Flow<List<TurnoEntity>>

    @Query(
        """
        SELECT t.*, p.nombre AS nombrePaciente FROM turnos t
        JOIN pacientes p ON p.id = t.pacienteId WHERE t.id = :id
        """
    )
    suspend fun obtenerConPaciente(id: Long): TurnoConNombre?

    @Query("SELECT * FROM turnos WHERE id = :id")
    suspend fun obtener(id: Long): TurnoEntity?

    /** Candidatos a superposición; la regla final la decide ReglasAgenda. */
    @Query("SELECT * FROM turnos WHERE estado != 'CANCELADO' AND inicio < :hasta AND fin > :desde")
    suspend fun activosEntre(desde: LocalDateTime, hasta: LocalDateTime): List<TurnoEntity>

    @Query(
        """
        SELECT COUNT(*) FROM turnos
        WHERE pacienteId = :pacienteId AND estado = 'CANCELADO' AND inicio >= :desde AND inicio < :hasta
        """
    )
    fun observarCanceladosEntre(pacienteId: Long, desde: LocalDateTime, hasta: LocalDateTime): Flow<Int>

    @Insert
    suspend fun insertar(turno: TurnoEntity): Long

    @Update
    suspend fun actualizar(turno: TurnoEntity): Int

    @Insert
    suspend fun insertarCambio(cambio: CambioTurnoEntity): Long
}

@Dao
interface BloqueoDao {
    @Query("SELECT * FROM bloqueos WHERE inicio < :hasta AND fin > :desde ORDER BY inicio")
    fun observarEntre(desde: LocalDateTime, hasta: LocalDateTime): Flow<List<BloqueoEntity>>

    @Query("SELECT * FROM bloqueos WHERE inicio < :hasta AND fin > :desde")
    suspend fun entre(desde: LocalDateTime, hasta: LocalDateTime): List<BloqueoEntity>

    @Insert
    suspend fun insertar(bloqueo: BloqueoEntity): Long

    @Query("DELETE FROM bloqueos WHERE id = :id")
    suspend fun eliminar(id: Long)
}

@Dao
interface CobroDao {
    @Query("SELECT * FROM cargos_sesion WHERE turnoId = :turnoId")
    suspend fun cargoDeTurno(turnoId: Long): CargoSesionEntity?

    @Query("SELECT * FROM cargos_sesion WHERE id = :id")
    suspend fun cargo(id: Long): CargoSesionEntity?

    @Insert
    suspend fun insertarCargo(cargo: CargoSesionEntity): Long

    @Query("SELECT importeCentavos FROM cobros WHERE cargoId = :cargoId")
    suspend fun cobrosDeCargo(cargoId: Long): List<Long>

    @Insert
    suspend fun insertarCobro(cobro: CobroEntity): Long

    @Query(
        """
        SELECT c.id AS cargoId, c.turnoId, t.pacienteId, p.nombre AS nombrePaciente, t.inicio AS fechaSesion,
               c.importeCentavos, COALESCE((SELECT SUM(co.importeCentavos) FROM cobros co WHERE co.cargoId = c.id), 0) AS cobradoCentavos
        FROM cargos_sesion c
        JOIN turnos t ON t.id = c.turnoId
        JOIN pacientes p ON p.id = t.pacienteId
        """
    )
    fun observarCargos(): Flow<List<CargoConCobrado>>

    @Query(
        """
        SELECT c.id AS cargoId, c.turnoId, t.pacienteId, p.nombre AS nombrePaciente, t.inicio AS fechaSesion,
               c.importeCentavos, COALESCE((SELECT SUM(co.importeCentavos) FROM cobros co WHERE co.cargoId = c.id), 0) AS cobradoCentavos
        FROM cargos_sesion c
        JOIN turnos t ON t.id = c.turnoId
        JOIN pacientes p ON p.id = t.pacienteId
        WHERE c.turnoId = :turnoId
        """
    )
    suspend fun cargoConCobradoDeTurno(turnoId: Long): CargoConCobrado?

    @Query(
        """
        SELECT COALESCE(SUM(c.importeCentavos), 0)
             - COALESCE((SELECT SUM(co.importeCentavos) FROM cobros co
                         JOIN cargos_sesion c2 ON c2.id = co.cargoId
                         JOIN turnos t2 ON t2.id = c2.turnoId
                         WHERE t2.pacienteId = :pacienteId), 0)
        FROM cargos_sesion c JOIN turnos t ON t.id = c.turnoId
        WHERE t.pacienteId = :pacienteId
        """
    )
    fun observarSaldoPaciente(pacienteId: Long): Flow<Long>

    @Query(
        """
        SELECT t.pacienteId AS pacienteId,
               SUM(c.importeCentavos - COALESCE((SELECT SUM(co.importeCentavos) FROM cobros co WHERE co.cargoId = c.id), 0)) AS saldoCentavos
        FROM cargos_sesion c JOIN turnos t ON t.id = c.turnoId
        GROUP BY t.pacienteId
        """
    )
    fun observarSaldos(): Flow<List<SaldoPacienteFila>>
}
