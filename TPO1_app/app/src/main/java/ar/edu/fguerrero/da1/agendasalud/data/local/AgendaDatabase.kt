package ar.edu.fguerrero.da1.agendasalud.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ar.edu.fguerrero.da1.agendasalud.data.local.dao.BloqueoDao
import ar.edu.fguerrero.da1.agendasalud.data.local.dao.CobroDao
import ar.edu.fguerrero.da1.agendasalud.data.local.dao.PacienteDao
import ar.edu.fguerrero.da1.agendasalud.data.local.dao.TurnoDao
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.BloqueoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CambioTurnoEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CargoSesionEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.CobroEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.PacienteEntity
import ar.edu.fguerrero.da1.agendasalud.data.local.entity.TurnoEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        PacienteEntity::class,
        TurnoEntity::class,
        BloqueoEntity::class,
        CargoSesionEntity::class,
        CobroEntity::class,
        CambioTurnoEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AgendaDatabase : RoomDatabase() {
    abstract fun pacienteDao(): PacienteDao
    abstract fun turnoDao(): TurnoDao
    abstract fun bloqueoDao(): BloqueoDao
    abstract fun cobroDao(): CobroDao

    companion object {
        private const val NOMBRE = "agendasalud.db"

        /** Abre la base cifrada con SQLCipher (RNF02). La clave viene de Android Keystore. */
        fun crear(context: Context, clave: ByteArray, nombre: String = NOMBRE): AgendaDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AgendaDatabase::class.java, nombre)
                .openHelperFactory(SupportOpenHelperFactory(clave))
                .build()
        }
    }
}
