package ar.edu.fguerrero.da1.agendasalud.data.local

import android.content.Context
import ar.edu.fguerrero.da1.agendasalud.core.security.ClaveBaseDatos
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Estado de la base cifrada, que se abre recién después del login. */
sealed interface EstadoBase {
    data object Cerrada : EstadoBase
    data object Abriendo : EstadoBase
    data object Lista : EstadoBase
    data object Error : EstadoBase
}

/**
 * Dueña única de la base cifrada. Se abre de forma explícita con [abrir] después del login,
 * para poder mostrar un error en lugar de cerrar la app si falla, y se cierra con [cerrar]
 * al cerrar sesión. Los repositorios piden [db] al crearse, así que solo se crean con la base abierta.
 */
@Singleton
class BaseDatosCifrada @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val clave: ClaveBaseDatos,
) {
    private var abierta: AgendaDatabase? = null

    val db: AgendaDatabase get() = checkNotNull(abierta) { "La base todavía no se abrió" }

    private val _estado = MutableStateFlow<EstadoBase>(EstadoBase.Cerrada)
    val estado: StateFlow<EstadoBase> = _estado.asStateFlow()

    /**
     * Abre la base y fuerza una lectura para detectar ya mismo una clave inválida
     * (por ejemplo, si Android Keystore la invalidó). No registra detalles: pueden incluir datos.
     */
    suspend fun abrir() {
        if (_estado.value == EstadoBase.Lista || _estado.value == EstadoBase.Abriendo) return
        _estado.value = EstadoBase.Abriendo
        _estado.value = withContext(Dispatchers.IO) {
            try {
                val base = AgendaDatabase.crear(context, clave.obtener())
                base.openHelper.writableDatabase
                abierta = base
                EstadoBase.Lista
            } catch (e: Exception) {
                EstadoBase.Error
            }
        }
    }

    /**
     * Cierre de sesión: la base se cierra y la clave deja de estar en memoria hasta el próximo login.
     * Los datos NO se borran: la app es local-first y el celular es la única copia (ver respaldo).
     */
    fun cerrar() {
        abierta?.close()
        abierta = null
        _estado.value = EstadoBase.Cerrada
    }

    /**
     * Último recurso cuando la base no se puede abrir: borra base y clave y crea una vacía.
     * El usuario y la contraseña se conservan. Sin un respaldo, los datos anteriores se pierden.
     */
    suspend fun empezarDeCero() {
        withContext(Dispatchers.IO) {
            abierta?.close()
            abierta = null
            context.deleteDatabase(AgendaDatabase.NOMBRE)
            clave.borrar()
        }
        _estado.value = EstadoBase.Cerrada
        abrir()
    }
}
