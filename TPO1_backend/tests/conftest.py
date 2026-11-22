import os
import sys
from datetime import datetime, timezone

import pytest
from fastapi.testclient import TestClient

# Raíz de TPO1_backend en el path, igual que api/index.py
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from api.index import app  # noqa: E402
from app.core.database import get_database  # noqa: E402
from tests.mock_repository import MockDatabase  # noqa: E402

EMAIL = "ana@example.com"
CONTRASENIA = "contrasenia-segura-1"


@pytest.fixture
def db():
    return MockDatabase()


@pytest.fixture
def cliente(db):
    """API real con la base reemplazada por el MockRepository."""

    async def base_falsa():
        return db

    app.dependency_overrides[get_database] = base_falsa
    # raise_server_exceptions=False: un error interno llega como 500, como lo vería la app
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c
    app.dependency_overrides.clear()


def datos_registro(email: str = EMAIL, contrasenia: str = CONTRASENIA) -> dict:
    return {
        "email": email,
        "password": contrasenia,
        "encrypted_dek": "ZGVrLWNpZnJhZGE=",
        "dek_salt": "c2FsdA==",
        "dek_iv": "aXY=",
    }


def payload_sync(version: int = 1) -> dict:
    return {
        "payload": {
            "version": version,
            "client_timestamp": datetime.now(timezone.utc).isoformat(),
            "ciphertext": f"Y2lmcmFkby12{version}",
            "iv": "aXY=",
            "auth_tag": "dGFn",
            "metadata_resumen": {"pacientes": 3},
        }
    }


@pytest.fixture
def token(cliente) -> str:
    respuesta = cliente.post("/api/v1/auth/register", json=datos_registro())
    assert respuesta.status_code == 201, respuesta.text
    return respuesta.json()["access_token"]


def auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}
