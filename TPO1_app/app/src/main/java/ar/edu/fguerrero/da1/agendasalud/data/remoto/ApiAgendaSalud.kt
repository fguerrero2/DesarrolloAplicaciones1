package ar.edu.fguerrero.da1.agendasalud.data.remoto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * API del backend del equipo (TPO1_backend, FastAPI). Los nombres de los campos son los de
 * TPO1_backend/app/models/schemas.py: si el backend cambia un esquema, hay que cambiarlo acá.
 */
interface ApiAgendaSalud {
    @POST("api/v1/auth/register")
    suspend fun registrar(@Body pedido: RegistroPedido): TokenRespuesta

    @POST("api/v1/auth/login")
    suspend fun iniciarSesion(@Body pedido: LoginPedido): TokenRespuesta

    @POST("api/v1/auth/recover-password")
    suspend fun recuperarContrasenia(@Body pedido: RecuperacionPedido): RespuestaGenerica

    @POST("api/v1/auth/reset-password")
    suspend fun restablecerContrasenia(@Body pedido: RestablecimientoPedido): RespuestaGenerica

    /** Calendario de sincronización definido en sync_config.json (no requiere token). */
    @GET("api/v1/sync/schedule")
    suspend fun calendario(): CalendarioRespuesta

    @POST("api/v1/sync/push")
    suspend fun subir(@Body pedido: SubidaPedido): RespuestaGenerica

    @GET("api/v1/sync/pull")
    suspend fun bajar(): BajadaRespuesta
}

// Autenticación. "password" NO es la contraseña: es la credencial derivada (ver ClavesCuenta).

@Serializable
data class RegistroPedido(
    val email: String,
    val password: String,
    @SerialName("nombre_profesional") val nombreProfesional: String = "Profesional",
    @SerialName("encrypted_dek") val encryptedDek: String,
    @SerialName("dek_salt") val dekSalt: String,
    @SerialName("dek_iv") val dekIv: String,
)

@Serializable
data class LoginPedido(val email: String, val password: String)

@Serializable
data class TokenRespuesta(
    @SerialName("access_token") val accessToken: String,
    @SerialName("tenant_id") val tenantId: String,
    val email: String,
    @SerialName("encrypted_dek") val encryptedDek: String,
    @SerialName("dek_salt") val dekSalt: String,
    @SerialName("dek_iv") val dekIv: String,
)

@Serializable
data class RecuperacionPedido(val email: String)

@Serializable
data class RestablecimientoPedido(
    val token: String,
    @SerialName("new_password") val nuevaPassword: String,
    @SerialName("new_encrypted_dek") val nuevoEncryptedDek: String? = null,
    @SerialName("new_dek_salt") val nuevoDekSalt: String? = null,
    @SerialName("new_dek_iv") val nuevoDekIv: String? = null,
)

@Serializable
data class RespuestaGenerica(val success: Boolean, val message: String)

// Sincronización: una copia completa de los datos, cifrada en el celular.

@Serializable
data class DatosCifradosDto(
    val version: Int,
    /** ISO-8601 con zona, por ejemplo 2026-11-03T21:00:00Z. Decide qué copia gana si hay conflicto. */
    @SerialName("client_timestamp") val clientTimestamp: String,
    val ciphertext: String,
    val iv: String,
    @SerialName("auth_tag") val authTag: String,
    /** Solo cantidades (nunca nombres): el backend las usa como telemetría. */
    @SerialName("metadata_resumen") val metadataResumen: Map<String, Int>? = null,
)

@Serializable
data class SubidaPedido(val payload: DatosCifradosDto)

@Serializable
data class BajadaRespuesta(
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("has_data") val hayDatos: Boolean,
    val version: Int? = null,
    val payload: DatosCifradosDto? = null,
)

@Serializable
data class CalendarioRespuesta(
    val version: String,
    @SerialName("sync_policy") val politica: JsonObject,
    @SerialName("conflict_resolution") val resolucionConflictos: String,
)
