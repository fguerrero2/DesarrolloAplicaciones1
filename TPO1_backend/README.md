# TPO1_backend — Backend Serverless para AgendaSalud

> **Materia:** Desarrollo de Aplicaciones 1 — UADE (2C 2026)  
> **Servicio:** API REST Serverless con FastAPI para persistencia remota, sincronización bidireccional y recuperación ante desastres con cifrado Zero-Knowledge.  
> **Plataforma de Despliegue:** Vercel (`@vercel/python`) en un único deploy.  
> **Base de Datos:** MongoDB Atlas (Cloud NoSQL) con compatibilidad para Neon Postgres.

---

## 1. Características Principales

1. **Aislamiento Multi-Tenant (`tenant_id`):** Cada profesional es una entidad independiente. Todas las operaciones de datos están aisladas por usuario.
2. **Cifrado Zero-Knowledge (E2E en Reposo):** Los datos sensibles de pacientes, turnos, agendas, cobros y saldos se almacenan **cifrados en el backend**. El servidor jamás tiene acceso en texto plano.
3. **Clave de Cifrado Protegida por el Usuario:** La clave de datos de la app (DEK) se custodia en el backend mediante un **Sobre Digital (Envelope Encryption)** cifrado con la clave derivada de la contraseña del usuario (KEK). Solo el usuario puede desenvolverla y recuperarla.
4. **Sincronización Bidireccional:**
   - **Push (App ➔ Backend):** Envío incremental y automático de datos cifrados.
   - **Pull / Restore (Backend ➔ App):** Recuperación completa en caso de cambio o pérdida de dispositivo celular.
5. **Calendario de Sincronización Centralizado:** Configurable globalmente para todos los usuarios desde el archivo [`sync_config.json`](./sync_config.json). La app móvil lee esta configuración (`GET /api/v1/sync/schedule`) y la aplica con `WorkManager`.
6. **Recuperación de Contraseña por Correo Electrónico:** Flujo con token seguro temporal (`POST /api/v1/auth/recover-password` y `POST /api/v1/auth/reset-password`).
7. **Deploy Único en Vercel:** Configuración lista para Vercel Serverless Functions (`vercel.json` y `api/index.py`).

---

## 2. Estructura de Carpetas

```
TPO1_backend/
├── api/
│   ├── __init__.py
│   └── index.py                 # Punto de entrada ASGI para Vercel Serverless Functions
├── app/
│   ├── __init__.py
│   ├── core/
│   │   ├── config.py            # Carga de variables de entorno y política de sincronización
│   │   ├── database.py          # Conexión persistente y pooling para MongoDB Atlas
│   │   ├── email.py             # Envío asincrónico de mails para restablecer contraseñas
│   │   └── security.py          # Hashing bcrypt y generación de tokens JWT
│   ├── models/
│   │   └── schemas.py           # Modelos Pydantic v2 (Auth, Sync, Payloads cifrados)
│   ├── routers/
│   │   ├── auth.py              # Endpoints de Registro, Login y Recuperación
│   │   └── sync.py              # Endpoints de Push, Pull y Calendario
│   └── services/
│       └── __init__.py
├── sync_config.json             # Política de sincronización global configurable
├── vercel.json                  # Manifiesto de despliegue para Vercel
├── requirements.txt             # Dependencias del backend
├── .env.example                 # Variables de entorno requeridas
└── README.md                    # Esta guía
```

---

## 3. Endpoints Principales

| Método | Endpoint | Descripción | Requiere Auth |
|---|---|---|:---:|
| `GET` | `/` | Estado del backend y links a documentación Swagger | No |
| `GET` | `/health` | Chequeo de salud del servicio | No |
| `POST` | `/api/v1/auth/register` | Registro de usuario y guardado de sobre cifrado de la clave | No |
| `POST` | `/api/v1/auth/login` | Login, emisión de JWT y entrega del sobre cifrado | No |
| `POST` | `/api/v1/auth/recover-password` | Envío de correo con token para recuperar contraseña | No |
| `POST` | `/api/v1/auth/reset-password` | Restablecimiento de contraseña con token | No |
| `GET` | `/api/v1/sync/schedule` | Consulta del calendario de sincronización (`sync_config.json`) | No |
| `POST` | `/api/v1/sync/push` | Respaldo de datos cifrados de la app hacia el backend | Sí (JWT) |
| `GET` | `/api/v1/sync/pull` | Restauración de datos cifrados desde el backend a la app | Sí (JWT) |

---

## 4. Ejecución Local para Pruebas

### 4.1 Opción A: Con Docker y Docker Compose (Recomendada)
Esta opción levanta automáticamente la API de FastAPI y una base de datos MongoDB lista para usar en un entorno aislado:

1. **Construir la imagen de Docker:**
   ```bash
   cd TPO1_backend
   docker build -t tpo1_backend:latest .
   ```

2. **Levantar el backend y MongoDB:**
   ```bash
   docker compose up -d
   ```

3. **Verificar que los contenedores estén corriendo:**
   ```bash
   docker ps --filter "name=tpo1_backend"
   ```

4. **Ejecutar la suite completa de pruebas End-to-End (E2E):**
   ```bash
   docker exec tpo1_backend_api python /app/test_e2e_local.py http://localhost:8000
   ```
   *Esta suite prueba automáticamente: Healthcheck, lectura de `sync_config.json`, generación y resguardo de clave DEK interna, registro, login, subida de datos cifrados (Push), restauración y descifrado (Pull) y solicitud de reseteo de contraseña.*

5. **Acceder a la documentación interactiva en el navegador:**
   - **Swagger UI:** [http://localhost:8000/docs](http://localhost:8000/docs)
   - **ReDoc:** [http://localhost:8000/redoc](http://localhost:8000/redoc)

6. **Detener los contenedores:**
   ```bash
   docker compose down
   ```

### 4.2 Opción B: Ejecución nativa con Python y Uvicorn
1. Crear un entorno virtual e instalar dependencias:
   ```bash
   cd TPO1_backend
   python3 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
   ```

2. Configurar el archivo `.env`:
   ```bash
   cp .env.example .env
   # Editar .env con tus credenciales de MongoDB Atlas y SMTP
   ```

3. Iniciar el servidor de desarrollo:
   ```bash
   uvicorn api.index:app --reload --port 8000
   ```

4. Abrir la documentación interactiva en el navegador:
   - **Swagger UI:** [http://127.0.0.1:8000/docs](http://127.0.0.1:8000/docs)
   - **ReDoc:** [http://127.0.0.1:8000/redoc](http://127.0.0.1:8000/redoc)

---

## 5. Despliegue en Vercel (Un Solo Deploy)

### Opción A: Mediante Vercel CLI
```bash
cd TPO1_backend
vercel
```
1. Seguir los prompts en pantalla para vincular al proyecto.
2. Configurar las variables de entorno en el panel de Vercel (Settings → Environment Variables):
   - `MONGODB_URI`: URI de conexión a MongoDB Atlas.
   - `SECRET_KEY`: Clave secreta para firma JWT.
   - `SMTP_HOST`, `SMTP_USER`, `SMTP_PASSWORD`: (Opcionales si se desea envío real de correo).
3. Desplegar a producción:
   ```bash
   vercel --prod
   ```

### Opción B: Mediante Git / GitHub (Monorepo)
1. Realizar commit y push del repositorio a GitHub.
2. En el panel de Vercel, importar el repositorio y definir como **Root Directory** la carpeta `TPO1_backend`.
3. Cargar las variables de entorno requeridas (`MONGODB_URI`, `SECRET_KEY`, etc.) y presionar **Deploy**.

#### 💡 Cómo evitar que los commits de la app Android (`TPO1_app`) disparen deploys innecesarios:
Para que Vercel ignore los commits de los otros programadores que modifiquen `TPO1_app/` o `docs/`:
1. Ir a tu proyecto en Vercel → **Settings** → **Git**.
2. Buscar la sección **Ignored Build Step**.
3. Seleccionar la opción **"Custom"** y colocar el siguiente comando:
   ```bash
   git diff --quiet HEAD^ HEAD .
   ```
4. Guardar los cambios.
*¿Cómo funciona?* Al estar configurado el **Root Directory** en `TPO1_backend`, este comando evalúa si hubo cambios dentro de esa carpeta. Si el commit solo modificó código de Android (`TPO1_app/`), el comando finaliza con código `0` y Vercel cancela el despliegue automáticamente sin consumir minutos ni generar builds innecesarios.

---

## 6. Configuración de Sincronización Automática

El archivo [`sync_config.json`](./sync_config.json) define las reglas de sincronización para toda la flota de usuarios:
```json
{
  "sync_policy": {
    "automatic_enabled": true,
    "interval_minutes": 360,
    "require_unmetered_network": true,
    "require_battery_not_low": true
  }
}
```
Cualquier cambio en este archivo en el backend es adoptado automáticamente por la app móvil en su siguiente ciclo de consulta sin necesidad de actualizar la aplicación en la tienda.

