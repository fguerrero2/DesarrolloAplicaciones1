from datetime import datetime
from typing import Any, Dict, Optional
from pydantic import BaseModel, EmailStr, Field


# ==========================================
# Esquemas de Autenticación y Tenant
# ==========================================

class UserRegisterRequest(BaseModel):
    """
    Registro de nuevo profesional (Tenant).
    Incluye el sobre criptográfico que protege la clave de datos de la app.
    El backend NUNCA recibe la clave en claro.
    """
    email: EmailStr
    password: str = Field(..., min_length=8, description="Contraseña maestra del usuario")
    nombre_profesional: Optional[str] = "Profesional"
    
    # Sobre Criptográfico de la Clave de Cifrado de la App (DEK)
    encrypted_dek: str = Field(..., description="Clave de datos cifrada en base64 (cifrada con KEK del usuario)")
    dek_salt: str = Field(..., description="Salt utilizado para la derivación de la KEK (PBKDF2/Argon2)")
    dek_iv: str = Field(..., description="Vector de inicialización para el cifrado de la DEK")


class UserLoginRequest(BaseModel):
    email: EmailStr
    password: str


class TokenResponse(BaseModel):
    access_token: str
    token_type: str = "bearer"
    tenant_id: str
    email: EmailStr
    # Datos del sobre criptográfico para que la app pueda recuperar su DEK
    encrypted_dek: str
    dek_salt: str
    dek_iv: str


class PasswordRecoveryRequest(BaseModel):
    email: EmailStr


class PasswordResetConfirmRequest(BaseModel):
    token: str = Field(..., description="Token de recuperación recibido por email")
    new_password: str = Field(..., min_length=8)
    # Re-envoltura opcional de la DEK con la nueva contraseña
    new_encrypted_dek: Optional[str] = None
    new_dek_salt: Optional[str] = None
    new_dek_iv: Optional[str] = None


class GenericResponse(BaseModel):
    success: bool
    message: str


# ==========================================
# Esquemas de Sincronización (Zero-Knowledge)
# ==========================================

class EncryptedDataPayload(BaseModel):
    """
    Carga útil de datos cifrados de la app (pacientes, turnos, bloqueos, cobros y saldos).
    El backend no puede leer ni interpretar estos campos.
    """
    version: int = Field(..., description="Número secuencial de versión o epoch de sincronización")
    client_timestamp: datetime = Field(..., description="Marca de tiempo en el dispositivo")
    ciphertext: str = Field(..., description="Blob cifrado en Base64 con AES-256-GCM")
    iv: str = Field(..., description="Vector de inicialización en Base64")
    auth_tag: str = Field(..., description="Tag de autenticación GCM en Base64")
    metadata_resumen: Optional[Dict[str, Any]] = Field(
        default=None,
        description="Metadatos no sensibles para telemetría (ej: cantidad de registros sincronizados)"
    )


class SyncPushRequest(BaseModel):
    payload: EncryptedDataPayload


class SyncPullResponse(BaseModel):
    tenant_id: str
    has_data: bool
    version: Optional[int] = None
    last_synced_at: Optional[datetime] = None
    payload: Optional[EncryptedDataPayload] = None


class SyncScheduleResponse(BaseModel):
    version: str
    sync_policy: Dict[str, Any]
    conflict_resolution: str

