"""
Casos de prueba de la API con el MockRepository (sin MongoDB).

Cubren el contrato que usa la app Android (ApiAgendaSalud.kt): rutas, códigos HTTP y campos de las
respuestas. Si una modificación del backend rompe ese contrato, estos tests fallan antes del deploy.

Uso:
    pip install -r requirements-dev.txt
    pytest
"""

from app.core.security import create_password_reset_token
from tests.conftest import CONTRASENIA, EMAIL, auth, datos_registro, payload_sync

REGISTRO = "/api/v1/auth/register"
LOGIN = "/api/v1/auth/login"
RECUPERAR = "/api/v1/auth/recover-password"
RESTABLECER = "/api/v1/auth/reset-password"
PUSH = "/api/v1/sync/push"
PULL = "/api/v1/sync/pull"
CALENDARIO = "/api/v1/sync/schedule"


# ---------- BE-01 a BE-03: Estado ----------

def test_be01_raiz_y_health_responden(cliente):
    assert cliente.get("/").json()["status"] == "online"
    assert cliente.get("/health").json()["status"] == "healthy"


def test_be02_rutas_que_usa_la_app_existen(cliente):
    rutas = cliente.get("/openapi.json").json()["paths"]
    for ruta in (REGISTRO, LOGIN, RECUPERAR, RESTABLECER, PUSH, PULL, CALENDARIO):
        assert ruta in rutas, f"La app llama a {ruta} y el backend ya no la expone"


def test_be03_calendario_de_sincronizacion(cliente):
    cuerpo = cliente.get(CALENDARIO).json()
    assert cuerpo["sync_policy"]["interval_minutes"] > 0
    assert cuerpo["conflict_resolution"]


# ---------- BE-04 a BE-10: Cuenta ----------

def test_be04_registro_devuelve_token_y_sobre_de_la_clave(cliente, db):
    cuerpo = cliente.post(REGISTRO, json=datos_registro()).json()
    assert cuerpo["access_token"] and cuerpo["tenant_id"].startswith("tenant_")
    assert cuerpo["encrypted_dek"] == "ZGVrLWNpZnJhZGE="
    usuario = db["users"].documentos[0]
    assert usuario["hashed_password"] != CONTRASENIA, "La contraseña nunca se guarda en claro"


def test_be05_email_se_normaliza_a_minusculas(cliente):
    cliente.post(REGISTRO, json=datos_registro(email="Ana@Example.com"))
    assert cliente.post(LOGIN, json={"email": EMAIL, "password": CONTRASENIA}).status_code == 200


def test_be06_email_repetido_devuelve_400(cliente):
    cliente.post(REGISTRO, json=datos_registro())
    assert cliente.post(REGISTRO, json=datos_registro()).status_code == 400


def test_be07_datos_invalidos_devuelven_422(cliente):
    assert cliente.post(REGISTRO, json=datos_registro(email="no-es-email")).status_code == 422
    assert cliente.post(REGISTRO, json=datos_registro(contrasenia="corta")).status_code == 422


def test_be08_login_correcto_devuelve_el_mismo_tenant(cliente):
    tenant = cliente.post(REGISTRO, json=datos_registro()).json()["tenant_id"]
    cuerpo = cliente.post(LOGIN, json={"email": EMAIL, "password": CONTRASENIA}).json()
    assert cuerpo["tenant_id"] == tenant
    assert cuerpo["dek_salt"] == "c2FsdA=="


def test_be09_login_incorrecto_devuelve_401(cliente):
    cliente.post(REGISTRO, json=datos_registro())
    assert cliente.post(LOGIN, json={"email": EMAIL, "password": "otra-contrasenia"}).status_code == 401
    assert cliente.post(LOGIN, json={"email": "nadie@example.com", "password": "x"}).status_code == 401


def test_be10_cuenta_inactiva_devuelve_403(cliente, db):
    cliente.post(REGISTRO, json=datos_registro())
    db["users"].documentos[0]["is_active"] = False
    assert cliente.post(LOGIN, json={"email": EMAIL, "password": CONTRASENIA}).status_code == 403


# ---------- BE-11 a BE-13: Recuperación de contraseña ----------

def test_be11_recuperar_no_revela_si_el_email_existe(cliente):
    cliente.post(REGISTRO, json=datos_registro())
    existe = cliente.post(RECUPERAR, json={"email": EMAIL})
    no_existe = cliente.post(RECUPERAR, json={"email": "nadie@example.com"})
    assert existe.status_code == no_existe.status_code == 200
    assert existe.json()["success"] and no_existe.json()["success"]


def test_be12_restablecer_cambia_la_contrasenia_y_el_sobre(cliente, db):
    cliente.post(REGISTRO, json=datos_registro())
    token = create_password_reset_token(email=EMAIL)
    respuesta = cliente.post(RESTABLECER, json={
        "token": token,
        "new_password": "nueva-contrasenia-2",
        "new_encrypted_dek": "bnVldm8=",
        "new_dek_salt": "c2FsMg==",
        "new_dek_iv": "aXYy",
    })
    assert respuesta.status_code == 200
    assert cliente.post(LOGIN, json={"email": EMAIL, "password": CONTRASENIA}).status_code == 401
    login = cliente.post(LOGIN, json={"email": EMAIL, "password": "nueva-contrasenia-2"}).json()
    assert login["encrypted_dek"] == "bnVldm8="


def test_be13_token_de_recuperacion_invalido_devuelve_400(cliente):
    respuesta = cliente.post(RESTABLECER, json={"token": "falso", "new_password": "nueva-contrasenia-2"})
    assert respuesta.status_code == 400


# ---------- BE-14 a BE-19: Sincronización ----------

def test_be14_pull_sin_datos_devuelve_has_data_false(cliente, token):
    cuerpo = cliente.get(PULL, headers=auth(token)).json()
    assert cuerpo["has_data"] is False and cuerpo["payload"] is None


def test_be15_push_y_pull_devuelven_la_ultima_version(cliente, token, db):
    cliente.post(PUSH, json=payload_sync(1), headers=auth(token))
    cliente.post(PUSH, json=payload_sync(2), headers=auth(token))
    cuerpo = cliente.get(PULL, headers=auth(token)).json()
    assert cuerpo["has_data"] is True and cuerpo["version"] == 2
    assert cuerpo["payload"]["ciphertext"] == payload_sync(2)["payload"]["ciphertext"]
    assert len(db["sync_snapshots"].documentos) == 2, "Se guarda el historial de versiones"


def test_be16_cada_tenant_ve_solo_sus_datos(cliente, token):
    cliente.post(PUSH, json=payload_sync(1), headers=auth(token))
    otro = cliente.post(REGISTRO, json=datos_registro(email="beto@example.com")).json()["access_token"]
    assert cliente.get(PULL, headers=auth(otro)).json()["has_data"] is False


def test_be17_sync_sin_token_o_con_token_invalido_se_rechaza(cliente):
    assert cliente.get(PULL).status_code in (401, 403)
    assert cliente.get(PULL, headers=auth("token-falso")).status_code == 401


def test_be18_token_de_usuario_borrado_devuelve_401(cliente, token, db):
    db["users"].documentos.clear()
    assert cliente.get(PULL, headers=auth(token)).status_code == 401


def test_be19_push_incompleto_devuelve_422(cliente, token):
    assert cliente.post(PUSH, json={"payload": {"version": 1}}, headers=auth(token)).status_code == 422


# ---------- BE-20: Falla de la base ----------

def test_be20_base_caida_devuelve_500_sin_colgarse(cliente, db):
    db.fallar = True
    assert cliente.post(LOGIN, json={"email": EMAIL, "password": CONTRASENIA}).status_code == 500
    # Los endpoints que no usan la base siguen funcionando
    assert cliente.get(CALENDARIO).status_code == 200
