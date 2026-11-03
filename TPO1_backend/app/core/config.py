import json
import os
from pathlib import Path
from typing import Any, Dict, Optional
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    ENVIRONMENT: str = "production"
    PROJECT_NAME: str = "AgendaSalud Backend"
    API_V1_STR: str = "/api/v1"
    
    # JWT & Seguridad
    SECRET_KEY: str = "clave-secreta-temporal-debe-cambiarse-en-produccion"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24  # 1 día
    PASSWORD_RESET_TOKEN_EXPIRE_MINUTES: int = 30  # 30 minutos
    
    # Base de Datos MongoDB Atlas
    MONGODB_URI: str = "mongodb://localhost:27017"
    MONGODB_DB_NAME: str = "agendasalud_tpo1"
    
    # Base de Datos Alternativa Neon Postgres
    DATABASE_URL: Optional[str] = None
    
    # Configuración SMTP
    SMTP_HOST: Optional[str] = None
    SMTP_PORT: int = 587
    SMTP_USER: Optional[str] = None
    SMTP_PASSWORD: Optional[str] = None
    SMTP_FROM_EMAIL: str = "noreply@agendasalud.app"
    SMTP_FROM_NAME: str = "AgendaSalud Soporte"
    FRONTEND_RESET_PASSWORD_URL: str = "agendasalud://reset-password"
    
    # Ruta al archivo de configuración de sincronización
    SYNC_CONFIG_PATH: str = str(Path(__file__).resolve().parent.parent.parent / "sync_config.json")

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=True,
        extra="ignore"
    )

    def get_sync_policy(self) -> Dict[str, Any]:
        """Lee y devuelve la política de sincronización configurada para todos los usuarios."""
        possible_paths = [
            Path(self.SYNC_CONFIG_PATH),
            Path(__file__).resolve().parent.parent.parent / "sync_config.json",
            Path.cwd() / "sync_config.json",
            Path("/var/task/sync_config.json")
        ]
        for p in possible_paths:
            if p.exists():
                try:
                    with open(p, "r", encoding="utf-8") as f:
                        return json.load(f)
                except Exception:
                    continue
        # Valor de respaldo predeterminado
        return {
            "version": "1.0.0",
            "sync_policy": {
                "automatic_enabled": True,
                "interval_minutes": 360,
                "require_unmetered_network": True,
                "require_battery_not_low": True
            }
        }


settings = Settings()

