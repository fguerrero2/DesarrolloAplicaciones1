from datetime import datetime, timezone
from fastapi import APIRouter, Depends, HTTPException, status
from motor.motor_asyncio import AsyncIOMotorDatabase

from app.core.config import settings
from app.core.database import get_database
from app.routers.auth import get_current_tenant
from app.models.schemas import (
    SyncPushRequest,
    SyncPullResponse,
    SyncScheduleResponse,
    GenericResponse,
    EncryptedDataPayload
)

router = APIRouter(prefix="/sync", tags=["Sincronización y Respaldo Cifrado"])


@router.get("/schedule", response_model=SyncScheduleResponse)
async def get_sync_schedule():
    """
    Entrega el calendario y política de sincronización configurada desde el backend
    en 'sync_config.json' para todos los usuarios de la aplicación móvil.
    """
    config_data = settings.get_sync_policy()
    return SyncScheduleResponse(
        version=config_data.get("version", "1.0.0"),
        sync_policy=config_data.get("sync_policy", {}),
        conflict_resolution=config_data.get("conflict_resolution", "LAST_WRITE_WINS_BY_TIMESTAMP")
    )


@router.post("/push", response_model=GenericResponse)
async def push_sync_data(
    request: SyncPushRequest,
    current_user: dict = Depends(get_current_tenant),
    db: AsyncIOMotorDatabase = Depends(get_database)
):
    """
    Persiste los datos de la app (pacientes, agendas, cobros y saldos) cifrados en el backend.
    Asocia estrictamente el registro al usuario autenticado (Tenant Isolation).
    Los datos sensibles se reciben y persisten cifrados (Zero-Knowledge).
    """
    tenant_id = current_user["tenant_id"]
    now_utc = datetime.now(timezone.utc)

    # Documento de instantánea o sincronización delta
    snapshot_doc = {
        "tenant_id": tenant_id,
        "version": request.payload.version,
        "client_timestamp": request.payload.client_timestamp,
        "server_timestamp": now_utc,
        # Carga útil estrictamente cifrada
        "ciphertext": request.payload.ciphertext,
        "iv": request.payload.iv,
        "auth_tag": request.payload.auth_tag,
        "metadata_resumen": request.payload.metadata_resumen
    }

    # Guardar en la colección de snapshots históricos
    await db["sync_snapshots"].insert_one(snapshot_doc)

    # Actualizar la instantánea más reciente del tenant (para pull rápido de restauración)
    await db["tenant_vault"].update_one(
        {"tenant_id": tenant_id},
        {"$set": {
            "tenant_id": tenant_id,
            "latest_version": request.payload.version,
            "last_synced_at": now_utc,
            "payload": {
                "version": request.payload.version,
                "client_timestamp": request.payload.client_timestamp,
                "ciphertext": request.payload.ciphertext,
                "iv": request.payload.iv,
                "auth_tag": request.payload.auth_tag,
                "metadata_resumen": request.payload.metadata_resumen
            }
        }},
        upsert=True
    )

    return GenericResponse(
        success=True,
        message=f"Datos cifrados sincronizados con éxito. Versión {request.payload.version} registrada."
    )


@router.get("/pull", response_model=SyncPullResponse)
async def pull_sync_data(
    current_user: dict = Depends(get_current_tenant),
    db: AsyncIOMotorDatabase = Depends(get_database)
):
    """
    Permite recuperar los datos desde el backend si el usuario cambia de dispositivo
    o si necesita restaurar su base de datos local.
    Devuelve la última copia cifrada vinculada al usuario.
    """
    tenant_id = current_user["tenant_id"]

    vault_entry = await db["tenant_vault"].find_one({"tenant_id": tenant_id})
    if not vault_entry or "payload" not in vault_entry:
        return SyncPullResponse(
            tenant_id=tenant_id,
            has_data=False,
            version=None,
            last_synced_at=None,
            payload=None
        )

    raw_payload = vault_entry["payload"]
    payload_obj = EncryptedDataPayload(
        version=raw_payload["version"],
        client_timestamp=raw_payload["client_timestamp"],
        ciphertext=raw_payload["ciphertext"],
        iv=raw_payload["iv"],
        auth_tag=raw_payload["auth_tag"],
        metadata_resumen=raw_payload.get("metadata_resumen")
    )

    return SyncPullResponse(
        tenant_id=tenant_id,
        has_data=True,
        version=vault_entry.get("latest_version"),
        last_synced_at=vault_entry.get("last_synced_at"),
        payload=payload_obj
    )

