package ar.edu.fguerrero.da1.agendasalud.core.security

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * Clave aleatoria de la base SQLCipher (RNF02). Se guarda cifrada con una clave de Android
 * Keystore que nunca sale del dispositivo. El archivo vive en noBackupFilesDir: la base de este
 * celular solo se abre en este celular (para recuperar datos en otro están el respaldo y la sincronización).
 */
class ClaveBaseDatos(context: Context) {

    private val archivo = File(context.noBackupFilesDir, "db.key")
    private val cifrador = CifradorKeystore(ALIAS)

    fun obtener(): ByteArray =
        if (archivo.exists()) cifrador.descifrar(archivo.readBytes()) else generar()

    /** Borra la clave guardada y la de Keystore. Sin ella la base anterior queda ilegible. */
    fun borrar() {
        archivo.delete()
        cifrador.borrarClave()
    }

    private fun generar(): ByteArray {
        val clave = ByteArray(LARGO_CLAVE).also { SecureRandom().nextBytes(it) }
        archivo.writeBytes(cifrador.cifrar(clave))
        return clave
    }

    private companion object {
        const val ALIAS = "agendasalud_db"
        const val LARGO_CLAVE = 32
    }
}
