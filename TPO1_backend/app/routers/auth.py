import uuid
from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from jose import jwt, JWTError
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.config import settings
from app.core.database import get_database
from app.core.security import (
    verify_password,
    get_password_hash,
    create_access_token,
    create_password_reset_token,
    verify_password_reset_token
)
from app.core.email import send_reset_password_email
from app.models.schemas import (
    UserRegisterRequest,
    UserLoginRequest,
    TokenResponse,
    PasswordRecoveryRequest,
    PasswordResetConfirmRequest,
    GenericResponse
)

router = APIRouter(prefix="/auth", tags=["Autenticación y Cuentas"])
security = HTTPBearer()


async def get_current_tenant(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: AsyncIOMotorDatabase = Depends(get_database)
) -> dict:
    """
    Dependencia de seguridad que valida el JWT y garantiza el aislamiento Multi-Tenant.
    Retorna el documento del usuario/tenant autenticado.
    """
    token = credentials.credentials
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Credenciales de autenticación inválidas o expiradas",
        headers={"WWW-Authenticate": "Bearer"},
    )
    try:
        payload = jwt.decode(token, settings.SECRET_KEY, algorithms=[settings.ALGORITHM])
        tenant_id: str = payload.get("sub")
        if tenant_id is None:
            raise credentials_exception
    except JWTError:
        raise credentials_exception

    user = await db["users"].find_one({"tenant_id": tenant_id})
    if user is None:
        raise credentials_exception
    return user


@router.post("/register", response_model=TokenResponse, status_code=status.HTTP_201_CREATED)
async def register_user(
    request: UserRegisterRequest,
    db: AsyncIOMotorDatabase = Depends(get_database)
):
    """
    Registra un nuevo usuario profesional (Tenant principal).
    Guarda el sobre criptográfico de la clave de la app (DEK) asociada al usuario.
    """
    # Verificar si el email ya existe
    existing_user = await db["users"].find_one({"email": request.email.lower()})
    if existing_user:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Ya existe una cuenta registrada con este correo electrónico"
        )

    tenant_id = f"tenant_{uuid.uuid4().hex[:12]}"
    hashed_password = get_password_hash(request.password)

    user_document = {
        "tenant_id": tenant_id,
        "email": request.email.lower(),
        "hashed_password": hashed_password,
        "nombre_profesional": request.nombre_profesional,
        # Sobre criptográfico: la clave de cifrado de la app protegida por el usuario
        "encrypted_dek": request.encrypted_dek,
        "dek_salt": request.dek_salt,
        "dek_iv": request.dek_iv,
        "created_at": datetime.now(timezone.utc),
        "updated_at": datetime.now(timezone.utc),
        "is_active": True
    }

    await db["users"].insert_one(user_document)

    # Generar token JWT
    access_token = create_access_token(subject=tenant_id)

    return TokenResponse(
        access_token=access_token,
        tenant_id=tenant_id,
        email=request.email.lower(),
        encrypted_dek=request.encrypted_dek,
        dek_salt=request.dek_salt,
        dek_iv=request.dek_iv
    )


@router.post("/login", response_model=TokenResponse)
async def login(
    request: UserLoginRequest,
    db: AsyncIOMotorDatabase = Depends(get_database)
):
    """
    Inicio de sesión del profesional.
    Devuelve el token JWT y el sobre criptográfico para que la app pueda recuperar su clave de datos.
    """
    user = await db["users"].find_one({"email": request.email.lower()})
    if not user or not verify_password(request.password, user.get("hashed_password", "")):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Correo electrónico o contraseña incorrectos"
        )

    if not user.get("is_active", True):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="La cuenta se encuentra inactiva"
        )

    access_token = create_access_token(subject=user["tenant_id"])

    return TokenResponse(
        access_token=access_token,
        tenant_id=user["tenant_id"],
        email=user["email"],
        encrypted_dek=user["encrypted_dek"],
        dek_salt=user["dek_salt"],
        dek_iv=user["dek_iv"]
    )


@router.post("/recover-password", response_model=GenericResponse)
async def recover_password(
    request: PasswordRecoveryRequest,
    db: AsyncIOMotorDatabase = Depends(get_database)
):
    """
    Solicitud de recuperación de contraseña por email.
    Genera un token seguro y envía un correo con el enlace correspondiente.
    """
    user = await db["users"].find_one({"email": request.email.lower()})
    # Siempre respondemos con éxito genérico para mitigar enumeración de usuarios
    if not user:
        return GenericResponse(
            success=True,
            message="Si el correo existe en nuestro sistema, se ha enviado un enlace de recuperación."
        )

    token = create_password_reset_token(email=user["email"])
    await send_reset_password_email(email_to=user["email"], token=token)

    return GenericResponse(
        success=True,
        message="Se ha enviado un correo con las instrucciones para restablecer tu contraseña."
    )


@router.post("/reset-password", response_model=GenericResponse)
async def reset_password(
    request: PasswordResetConfirmRequest,
    db: AsyncIOMotorDatabase = Depends(get_database)
):
    """
    Restablece la contraseña del usuario a partir del token recibido por correo.
    Permite opcionalmente re-envolver la clave interna de cifrado (DEK) con la nueva contraseña.
    """
    email = verify_password_reset_token(request.token)
    if not email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="El token de recuperación es inválido o ha expirado"
        )

    user = await db["users"].find_one({"email": email})
    if not user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Usuario no encontrado"
        )

    update_fields = {
        "hashed_password": get_password_hash(request.new_password),
        "updated_at": datetime.now(timezone.utc)
    }

    # Si la app envía el nuevo sobre de la DEK cifrado con la nueva contraseña
    if request.new_encrypted_dek and request.new_dek_salt and request.new_dek_iv:
        update_fields["encrypted_dek"] = request.new_encrypted_dek
        update_fields["dek_salt"] = request.new_dek_salt
        update_fields["dek_iv"] = request.new_dek_iv

    await db["users"].update_one(
        {"email": email},
        {"$set": update_fields}
    )

    return GenericResponse(
        success=True,
        message="La contraseña ha sido actualizada con éxito."
    )

