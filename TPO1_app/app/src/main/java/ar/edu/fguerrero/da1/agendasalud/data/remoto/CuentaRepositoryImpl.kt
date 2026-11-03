package ar.edu.fguerrero.da1.agendasalud.data.remoto

import ar.edu.fguerrero.da1.agendasalud.core.security.ClavesCuenta
import ar.edu.fguerrero.da1.agendasalud.core.security.SobreClave
import ar.edu.fguerrero.da1.agendasalud.domain.model.ErrorDominio
import ar.edu.fguerrero.da1.agendasalud.domain.model.Resultado
import ar.edu.fguerrero.da1.agendasalud.domain.repository.CuentaRepository
import ar.edu.fguerrero.da1.agendasalud.domain.repository.Restablecimiento
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CuentaRepositoryImpl @Inject constructor(
    private val api: ApiAgendaSalud,
    private val almacen: AlmacenCuenta,
) : CuentaRepository {

    override suspend fun registrar(email: String, contrasenia: CharArray): Resultado<Unit> =
        conContrasenia(contrasenia) { clave -> darDeAlta(ClavesCuenta.normalizar(email), clave) }

    override suspend fun iniciarSesion(email: String, contrasenia: CharArray): Resultado<Unit> =
        conContrasenia(contrasenia) { clave ->
            val correo = ClavesCuenta.normalizar(email)
            val credencial = ClavesCuenta.credencial(correo, clave)
            val respuesta = api.iniciarSesion(LoginPedido(correo, credencial))
            // Solo con la contraseña correcta se puede desenvolver la clave de datos
            almacen.guardarClaveDatos(desenvolver(respuesta, clave))
            guardar(respuesta, credencial)
        }

    override suspend fun conectarDespuesDeLoginLocal(email: String, contrasenia: CharArray) {
        val correo = ClavesCuenta.normalizar(email)
        val resultado = conContrasenia(contrasenia.copyOf()) { clave ->
            val credencial = ClavesCuenta.credencial(correo, clave)
            almacen.credencialEnMemoria = correo to credencial
            if (almacen.sesion() != null && almacen.claveDatos() != null) return@conContrasenia
            val respuesta = api.iniciarSesion(LoginPedido(correo, credencial))
            if (almacen.claveDatos() == null) almacen.guardarClaveDatos(desenvolver(respuesta, clave))
            guardar(respuesta, credencial)
        }
        // Cuenta creada antes de que existiera el backend: se da de alta con los datos locales
        // (solo si el usuario es un email, como pide el backend)
        if (resultado == Resultado.Fallo(ErrorDominio.CredencialesInvalidas) && almacen.claveDatos() == null) {
            conContrasenia(contrasenia.copyOf()) { clave -> darDeAlta(correo, clave) }
        }
        contrasenia.fill('\u0000')
    }

    override suspend fun pedirRecuperacion(email: String): Resultado<Unit> =
        llamar { api.recuperarContrasenia(RecuperacionPedido(ClavesCuenta.normalizar(email))) }

    override suspend fun restablecerContrasenia(token: String, nueva: CharArray): Resultado<Restablecimiento> {
        // El email va en el token firmado del mail ("sub"); se usa para derivar la nueva credencial
        val email = emailDelToken(token) ?: return Resultado.Fallo(ErrorDominio.EnlaceVencido)
        var resultado: Restablecimiento? = null
        val llamada = conContrasenia(nueva) { clave ->
            val claveDatos = almacen.claveDatos()
            // Sin la clave de datos (otro celular) los datos del servidor ya no se pueden leer:
            // se arranca con una clave nueva y la próxima sincronización sube lo de este celular
            val dek = claveDatos ?: ClavesCuenta.aleatorio(32)
            val salt = ClavesCuenta.aleatorio(16)
            val sobre = ClavesCuenta.envolver(ClavesCuenta.kek(clave, salt), dek)
            api.restablecerContrasenia(
                RestablecimientoPedido(token, ClavesCuenta.credencial(email, clave), sobre.encryptedDek, ClavesCuenta.b64(salt), sobre.iv)
            )
            if (claveDatos == null) almacen.guardarClaveDatos(dek)
            almacen.olvidarSesion()
            resultado = Restablecimiento(email, datosConservados = claveDatos != null)
        }
        return when (llamada) {
            is Resultado.Exito -> Resultado.Exito(resultado!!)
            // 400 en reset-password = token inválido o vencido
            is Resultado.Fallo -> Resultado.Fallo(if (llamada.error == ErrorDominio.UsuarioExistente) ErrorDominio.EnlaceVencido else llamada.error)
        }
    }

    /** Pide un token nuevo con la credencial en memoria (el JWT dura 1 día y no hay renovación). */
    suspend fun reconectar(): Boolean {
        val (email, credencial) = almacen.credencialEnMemoria ?: return false
        return llamar { guardar(api.iniciarSesion(LoginPedido(email, credencial)), credencial) } is Resultado.Exito
    }

    /** El backend usa JWT sin estado y no tiene endpoint de logout: se borra el token del celular. */
    override suspend fun cerrarSesion() {
        almacen.olvidarSesion()
        almacen.credencialEnMemoria = null
    }

    private suspend fun darDeAlta(email: String, clave: CharArray) {
        val credencial = ClavesCuenta.credencial(email, clave)
        val dek = almacen.claveDatos() ?: ClavesCuenta.aleatorio(32)
        val salt = ClavesCuenta.aleatorio(16)
        val sobre = ClavesCuenta.envolver(ClavesCuenta.kek(clave, salt), dek)
        val respuesta = api.registrar(RegistroPedido(email, credencial, encryptedDek = sobre.encryptedDek, dekSalt = ClavesCuenta.b64(salt), dekIv = sobre.iv))
        almacen.guardarClaveDatos(dek)
        guardar(respuesta, credencial)
    }

    private fun desenvolver(respuesta: TokenRespuesta, clave: CharArray): ByteArray =
        ClavesCuenta.desenvolver(ClavesCuenta.kek(clave, ClavesCuenta.deB64(respuesta.dekSalt)), SobreClave(respuesta.encryptedDek, respuesta.dekIv))

    private fun guardar(respuesta: TokenRespuesta, credencial: String) {
        almacen.guardarSesion(SesionRemota(respuesta.accessToken, respuesta.email))
        almacen.credencialEnMemoria = respuesta.email to credencial
    }

    private fun emailDelToken(token: String): String? = runCatching {
        val carga = Base64.getUrlDecoder().decode(token.split(".")[1].padEnd((token.split(".")[1].length + 3) / 4 * 4, '='))
        Json.parseToJsonElement(carga.decodeToString()).jsonObject["sub"]!!.jsonPrimitive.content
    }.getOrNull()

    /** PBKDF2 es lento: se deriva fuera del hilo principal y la contraseña se borra de memoria al terminar. */
    private suspend fun conContrasenia(contrasenia: CharArray, bloque: suspend (CharArray) -> Unit): Resultado<Unit> = try {
        llamar { bloque(contrasenia) }
    } finally {
        contrasenia.fill('\u0000')
    }

    /** Traduce los errores de red y HTTP del backend (FastAPI) a errores que la pantalla sabe mostrar. */
    private suspend fun llamar(bloque: suspend () -> Unit): Resultado<Unit> = try {
        withContext(Dispatchers.IO) { bloque() }
        Resultado.Exito(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Resultado.Fallo(ErrorDominio.SinConexion)
    } catch (e: HttpException) {
        Resultado.Fallo(
            when (e.code()) {
                400 -> ErrorDominio.UsuarioExistente // register: email ya registrado; reset: token inválido
                401 -> ErrorDominio.CredencialesInvalidas
                422 -> ErrorDominio.EmailInvalido // validación de Pydantic (email mal formado)
                else -> ErrorDominio.ErrorServidor
            }
        )
    } catch (e: Exception) {
        // Por ejemplo, la clave de datos no se pudo desenvolver
        Resultado.Fallo(ErrorDominio.ErrorServidor)
    }
}
