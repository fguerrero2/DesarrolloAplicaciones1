"""
Script de prueba End-to-End local para TPO1_backend.
Utiliza urllib.request (biblioteca estándar) para no requerir dependencias adicionales.
Puede ejecutarse localmente o dentro del contenedor Docker.

Uso:
    python test_e2e_local.py [URL_BASE]
    Ejemplo: python test_e2e_local.py http://localhost:8000
"""

import sys
import os
import json
import base64
import urllib.request
import urllib.error
from datetime import datetime, timezone

# Asegurar import de CryptoService
sys.path.append(os.path.abspath(os.path.dirname(__file__)))
from app.services.crypto_service import CryptoService

BASE_URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8000"


def http_request(method: str, url: str, data: dict = None, headers: dict = None):
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    
    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    
    try:
        with urllib.request.urlopen(req) as response:
            res_body = response.read().decode("utf-8")
            status = response.status
            return status, json.loads(res_body) if res_body else {}
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        return e.code, json.loads(err_body) if err_body else {}


def log_step(step_num: int, title: str):
    print(f"\n==================================================")
    print(f"[{step_num}] {title}")
    print(f"==================================================")


def run_tests():
    print(f"Iniciando pruebas E2E contra: {BASE_URL}")

    # 1. Healthcheck
    log_step(1, "Verificando estado de salud del backend")
    status, res = http_request("GET", f"{BASE_URL}/health")
    assert status == 200, f"Fallo /health ({status}): {res}"
    print(f"✔ /health OK: {res}")

    # 2. Consultar calendario de sincronización
    log_step(2, "Consultando calendario de sincronización configurable (sync_config.json)")
    status, schedule_data = http_request("GET", f"{BASE_URL}/api/v1/sync/schedule")
    assert status == 200, f"Fallo /sync/schedule ({status}): {schedule_data}"
    print(f"✔ Calendario obtenido: Intervalo = {schedule_data['sync_policy']['interval_minutes']} minutos")
    print(f"✔ Política de red: unmetered={schedule_data['sync_policy'].get('require_unmetered_network')}")

    # 3. Simulación de Registro con Cifrado de Extremo a Extremo (Envelope Encryption)
    log_step(3, "Generando claves internas del usuario (DEK y KEK) y registrando cuenta")
    test_email = f"lic.gonzalez_{int(datetime.now().timestamp())}@test.com"
    test_password = "PasswordSeguro2026!"
    
    # El dispositivo genera 32 bytes aleatorios para la DEK (clave interna de la app)
    dek = CryptoService.generate_random_bytes(32)
    salt = CryptoService.generate_random_bytes(16)
    salt_b64 = base64.b64encode(salt).decode("utf-8")
    
    # Derivamos la KEK a partir de la contraseña
    kek = CryptoService.derive_key(test_password, salt)
    
    # Envolvemos la DEK con la KEK
    encrypted_dek_b64, iv_dek_b64, tag_dek_b64 = CryptoService.wrap_key(kek, dek)

    register_payload = {
        "email": test_email,
        "password": test_password,
        "nombre_profesional": "Lic. Martín González",
        "encrypted_dek": encrypted_dek_b64,
        "dek_salt": salt_b64,
        "dek_iv": iv_dek_b64
    }

    status, reg_data = http_request("POST", f"{BASE_URL}/api/v1/auth/register", register_payload)
    assert status == 201, f"Fallo /register ({status}): {reg_data}"
    access_token = reg_data["access_token"]
    tenant_id = reg_data["tenant_id"]
    print(f"✔ Usuario registrado: Tenant ID = {tenant_id}")
    print(f"✔ Token JWT obtenido: {access_token[:20]}...")

    headers = {"Authorization": f"Bearer {access_token}"}

    # 4. Login y desenvolvimiento de la clave DEK
    log_step(4, "Iniciando sesión y desenvolviendo la clave de datos (DEK)")
    login_payload = {
        "email": test_email,
        "password": test_password
    }
    status, login_data = http_request("POST", f"{BASE_URL}/api/v1/auth/login", login_payload)
    assert status == 200, f"Fallo /login ({status}): {login_data}"
    
    # Verificamos que la app pueda recuperar su DEK original usando su contraseña
    recovered_salt = base64.b64decode(login_data["dek_salt"])
    recovered_kek = CryptoService.derive_key(test_password, recovered_salt)
    recovered_dek = CryptoService.unwrap_key(
        recovered_kek,
        login_data["encrypted_dek"],
        login_data["dek_iv"],
        tag_dek_b64
    )
    assert recovered_dek == dek, "La DEK recuperada no coincide con la original!"
    print(f"✔ Clave de datos (DEK) desenvuelta con éxito en el cliente. Clave verificada.")

    # 5. Sincronización Push (Subida de datos cifrados de la app)
    log_step(5, "Cifrando datos de pacientes, turnos, cobros y saldos para Push")
    app_data = {
        "pacientes": [
            {"id": 1, "nombre": "Juan Pérez", "telefono": "+5491100001111", "activo": True},
            {"id": 2, "nombre": "María Gómez", "telefono": "+5491122223333", "activo": True}
        ],
        "turnos": [
            {"id": 101, "pacienteId": 1, "inicio": "2026-11-04T10:00:00", "duracionMinutos": 50, "importeCentavos": 2500000, "estado": "PROGRAMADO"}
        ],
        "bloqueos": [
            {"id": 1, "inicio": "2026-11-04T13:00:00", "fin": "2026-11-04T14:00:00", "motivo": "Almuerzo / Curso"}
        ],
        "cobros_y_saldos": [
            {"pacienteId": 1, "cargoSesionId": 50, "importeCentavos": 2500000, "cobradoCentavos": 1000000, "saldoPendienteCentavos": 1500000}
        ]
    }

    # Ciframos el JSON con la DEK usando AES-256-GCM
    encrypted_payload_meta = CryptoService.encrypt_payload(dek, json.dumps(app_data))
    
    push_body = {
        "payload": {
            "version": 1,
            "client_timestamp": datetime.now(timezone.utc).isoformat(),
            "ciphertext": encrypted_payload_meta["ciphertext"],
            "iv": encrypted_payload_meta["iv"],
            "auth_tag": encrypted_payload_meta["auth_tag"],
            "metadata_resumen": {
                "total_pacientes": 2,
                "total_turnos": 1,
                "total_saldos_pendientes": 1
            }
        }
    }

    status, push_res = http_request("POST", f"{BASE_URL}/api/v1/sync/push", push_body, headers)
    assert status == 200, f"Fallo /sync/push ({status}): {push_res}"
    print(f"✔ /sync/push OK: {push_res['message']}")

    # 6. Sincronización Pull (Restauración de datos cifrados en nuevo dispositivo)
    log_step(6, "Descargando datos cifrados desde el backend (Pull / Restore) y descifrando")
    status, pull_data = http_request("GET", f"{BASE_URL}/api/v1/sync/pull", None, headers)
    assert status == 200, f"Fallo /sync/pull ({status}): {pull_data}"
    assert pull_data["has_data"] is True, "No se encontraron datos en el backend!"
    
    pulled_payload = pull_data["payload"]
    # Desciframos el payload recibido con la DEK
    decrypted_json_str = CryptoService.decrypt_payload(
        dek,
        pulled_payload["ciphertext"],
        pulled_payload["iv"],
        pulled_payload["auth_tag"]
    )
    restored_app_data = json.loads(decrypted_json_str)
    
    assert restored_app_data["pacientes"][0]["nombre"] == "Juan Pérez"
    assert restored_app_data["cobros_y_saldos"][0]["saldoPendienteCentavos"] == 1500000
    print(f"✔ Datos recuperados y descifrados con éxito!")
    print(f"  - Pacientes restaurados: {len(restored_app_data['pacientes'])}")
    print(f"  - Turnos restaurados: {len(restored_app_data['turnos'])}")
    print(f"  - Saldos pendientes restaurados: {restored_app_data['cobros_y_saldos'][0]['saldoPendienteCentavos']} centavos")

    # 7. Prueba de Recuperación de Contraseña
    log_step(7, "Solicitando token de recuperación de contraseña por email")
    status, recover_res = http_request("POST", f"{BASE_URL}/api/v1/auth/recover-password", {"email": test_email})
    assert status == 200, f"Fallo /recover-password ({status}): {recover_res}"
    print(f"✔ /auth/recover-password OK: {recover_res['message']}")

    print("\n==================================================")
    print("🎉 TODAS LAS PRUEBAS E2E PASARON SATISFACTORIAMENTE!")
    print("==================================================")


if __name__ == "__main__":
    try:
        run_tests()
    except Exception as e:
        print(f"\n❌ ERROR EN LA PRUEBA: {e}")
        sys.exit(1)
