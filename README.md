# DesarrolloAplicaciones1

> Repositorio de trabajo, documentación y trazabilidad de los TPO de la materia
> **Desarrollo de Aplicaciones I** — UADE — **2C 2026**

Incluye la documentación y el código fuente de cada TPO.

| Carpeta | Contenido |
|---|---|
| [`TPO1_app/`](./TPO1_app/) | App Android **AgendaSalud** (Kotlin + Jetpack Compose) |
| [`TPO1_backend/`](./TPO1_backend/) | Backend del equipo (FastAPI + MongoDB), desplegado en Vercel |
| [`docs/`](./docs/) | Consigna, preentrega, **documento de la entrega final** y **videos de la demo** |

---

# TPO1 — AgendaSalud

## Qué hace la aplicación

Los profesionales independientes de psicología, psicopedagogía y fonoaudiología suelen administrar turnos y cobros combinando una agenda de papel, WhatsApp y planillas. Así cuesta ver qué horarios están libres, mantener al día las reprogramaciones y saber cuánto debe cada paciente.

**AgendaSalud** es una app Android para que ese profesional gestione desde el celular su **agenda, pacientes y cobros**:

- Ve el día con los turnos, los **huecos libres** y los **períodos bloqueados** (vacaciones, cursos).
- Da turnos sin superposición y dentro de su horario de atención; completa, reprograma o cancela sesiones.
- Registra **cobros totales o parciales** (efectivo o Mercado Pago) y ve el **saldo pendiente** por paciente, calculado solo.
- Al reprogramar, prepara un **borrador de WhatsApp** para avisar al paciente (nunca se envía solo).
- Funciona **sin internet** (offline-first): todo se guarda cifrado en el celular y se **sincroniza cifrado** con el backend del equipo para no perder los datos al cambiar de celular.

**Problema → Usuario → Solución → Valor:** agenda y cobros dispersos → profesional independiente que atiende solo → app móvil offline-first con datos cifrados → ver la disponibilidad y los saldos en segundos, sin perder información.

## Demo en video

Grabados en un emulador Pixel 9a (Android 17) contra el backend del equipo en Docker, con datos de prueba. Están en [`docs/videos/`](./docs/videos/).

| Video | Qué muestra |
|---|---|
| [01_registro_y_guia.mp4](./docs/videos/01_registro_y_guia.mp4) (0:44) | Crear la cuenta, guía de bienvenida y el primer paso con la agenda vacía |
| [02_pacientes_turnos_y_bloqueos.mp4](./docs/videos/02_pacientes_turnos_y_bloqueos.mp4) (2:51) | Alta y búsqueda de pacientes, turnos en huecos libres, hora fuera de horario rechazada, bloqueo de un período (RF01–RF03) |
| [03_completar_sesion_y_cobros.mp4](./docs/videos/03_completar_sesion_y_cobros.mp4) (1:21) | Completar una sesión de $20.000, cobro parcial de $8.000, cobro del resto y anulación de un cobro (RF04) |
| [04_reprogramar_whatsapp_y_deshacer.mp4](./docs/videos/04_reprogramar_whatsapp_y_deshacer.mp4) (1:31) | Reprogramar con borrador de WhatsApp, cancelar y deshacer, quitar un bloqueo y deshacer (RF05) |
| [05_sincronizacion_y_sin_conexion.mp4](./docs/videos/05_sincronizacion_y_sin_conexion.mp4) (1:30) | Sincronizar, modo sin conexión, error con Reintentar y ayuda |

## Funcionalidades implementadas y requisitos alcanzados

| Requisito | Estado | Dónde se ve |
|---|---|---|
| RF01 Gestionar pacientes | ✅ | Pestaña Pacientes: alta, edición, búsqueda, ficha con historial, saldo y cancelaciones del año, desactivar/reactivar |
| RF02 Gestionar agenda de sesiones | ✅ | Hoy y Agenda: turnos en huecos libres, sin superposición, dentro del horario; completar, reprogramar, cancelar |
| RF03 Gestionar bloqueo de agendas | ✅ | Agenda → Bloquear período; no se permiten turnos en un período bloqueado |
| RF04 Registrar cobros y consultar saldos | ✅ | Registrar cobro al completar o desde Cobros; $20.000 − $8.000 = $12.000 pendiente; anular cobros |
| RF05 Reprogramación por WhatsApp | ✅ | Tras reprogramar: abrir WhatsApp o copiar el borrador |
| Agregado: cuenta y sincronización | ✅ | Registro con email, copia cifrada en el backend, recuperación en otro celular y de contraseña |
| Agregado: respaldo en archivo | ✅ | ⋮ → Exportar / Restaurar respaldo cifrado con contraseña propia |

**Experiencia de uso** (heurísticas de Nielsen): estados de carga, vacío, error y **sin conexión** en todas las pantallas; **Deshacer** al cancelar un turno, quitar un bloqueo o registrar un cobro; confirmación al completar una sesión y al cerrar sesión; hora limitada al horario de atención; buscador de pacientes; ayuda **¿Cómo funciona?** y guía de bienvenida. Detalle en [`TPO1_app/IMPLEMENTACION.md`](./TPO1_app/IMPLEMENTACION.md).

## Arquitectura final

MVVM + principios de Clean Architecture, offline-first:

```
UI (Jetpack Compose) → ViewModel (StateFlow<EstadoUi>) → Caso de uso / Repositorio (interfaz en dominio)
        ↑ Flow de Room                                    → Room + SQLCipher (fuente única de verdad)
                                                          → Sincronizador → Retrofit → TPO1_backend (Vercel) → MongoDB Atlas
```

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Presentación | `presentation/` | Una Activity por pantalla (Acceso, Hoy, Agenda, Pacientes, Cobros), diálogos en Compose, ViewModels con Hilt |
| Dominio | `domain/` | Modelos, reglas (`ReglasAgenda`, `ReglasCobro`, `HorarioAtencion`), casos de uso e interfaces de repositorio. Kotlin puro, sin Android |
| Datos | `data/` | Repositorios Room con transacciones, base cifrada con SQLCipher y clave en Android Keystore, Retrofit, sincronización con WorkManager |

- **Topología:** la app y sus datos viven en el celular. El backend guarda la cuenta y una copia **cifrada en el celular** (AES-256-GCM): el servidor no puede leer los datos de los pacientes. WhatsApp y el selector de archivos se usan mediante Intents.
- **Sincronización:** según el calendario que define el servidor (`sync_config.json`) o con ⋮ → Sincronizar ahora. Ante conflictos gana la copia más reciente.

Diagrama completo en la sección 23 del [documento de la entrega final](./docs/) y en [`TPO1_app/IMPLEMENTACION.md`](./TPO1_app/IMPLEMENTACION.md) (sección 4).

## Tecnologías utilizadas

| Tecnología | Para qué |
|---|---|
| Kotlin, Android (minSdk 26, targetSdk 37) | App nativa y APIs de seguridad del sistema |
| Jetpack Compose + Material 3 | Toda la interfaz |
| ViewModel, StateFlow, Coroutines | Estado de pantalla y trabajo fuera del hilo principal |
| Hilt | Inyección de dependencias |
| Room + SQLCipher + Android Keystore | Base local cifrada |
| DataStore | Preferencias no sensibles |
| Retrofit + kotlinx.serialization | Comunicación con el backend |
| WorkManager | Sincronización en segundo plano |
| BiometricPrompt | Huella o PIN como atajo de ingreso |
| FastAPI + MongoDB (backend) | Cuenta, recuperación de contraseña y copia cifrada; desplegado en Vercel |
| JUnit, coroutines-test, pytest | Tests de la app y del backend |

## Cómo ejecutarla

### App (Android Studio)

1. Abrir la carpeta **`TPO1_app`** (no la raíz del repo) en Android Studio y esperar el *Gradle sync*.
2. Elegir un emulador o celular con Android 8 o superior y tocar **Run ▶**.
3. La primera vez, tocar **Registrarse** con un email y una contraseña de al menos 8 caracteres.

Por defecto la app usa el backend desplegado en **Vercel** (`https://agendasalud-backend.vercel.app/`).

### Con el backend local (opcional)

```bash
cd TPO1_backend
docker compose up -d          # API en el puerto 8000 + MongoDB
```

y agregar `backendLocal=true` en `TPO1_app/local.properties`. El build debug pasa a usar `http://10.0.2.2:8000/` (la compu vista desde el emulador). Si el emulador no llega a esa dirección por su Wi‑Fi virtual, se puede usar `adb reverse tcp:8000 tcp:8000`.

### Desde la terminal

```bash
cd TPO1_app
./gradlew :app:assembleDebug        # APK en app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # 92 tests JVM
./gradlew :app:lintDebug            # análisis estático

cd ../TPO1_backend
pip install -r requirements-dev.txt && pytest   # 20 tests de la API
```

Para empezar de cero en el emulador: `adb shell pm clear ar.edu.fguerrero.da1.agendasalud` (borra todos los datos de la app).

## Pruebas

| Suite | Tests | Qué verifica |
|---|---|---|
| Reglas de dominio | 20 | Superposición, horario de atención, armado de la agenda, saldos |
| Casos de uso con `MockRepository` | 21 | Reservar, cancelar, reprogramar, bloquear, completar y cobrar |
| ViewModels con `MockRepository` | 14 | Lo que ven Agenda, Pacientes y Cobros ante cada acción |
| Sincronización | 9 | Indicador, éxito, causa de cada error, Reintentar y modo sin conexión |
| Usabilidad | 11 | Deshacer, anular cobros, guía, ayuda |
| Seguridad y formatos | 17 | Credenciales, claves, compatibilidad con el cifrado del backend, respaldo |
| Instrumentados | 3 | Base cifrada en disco y transacciones (requieren emulador) |
| API del backend (pytest) | 20 | Contrato con la app, cuenta, sincronización, aislamiento por usuario |

## Decisiones relevantes

- **Offline-first con Room como única fuente de verdad:** el profesional usa la app entre sesiones, con conectividad variable; ninguna pantalla espera a la red.
- **Datos cifrados en el celular y zero-knowledge en el servidor:** son datos de pacientes. La contraseña nunca viaja; el backend recibe una credencial derivada y una copia cifrada.
- **Usuario y contraseña propios** además del bloqueo del celular, con huella o PIN como atajo (cambio respecto de la preentrega).
- **Backend del equipo para recuperar los datos** al cambiar de celular, algo que la preentrega no ofrecía.
- **Cinco pantallas con diálogos** en lugar de navegar a pantallas nuevas, para reducir la carga cognitiva.
- **Mejoras de usabilidad** a partir de un análisis con las heurísticas de Nielsen.

## Limitaciones conocidas

- El horario de atención (lunes a viernes 9 a 18, sábados 9 a 12) está fijo en el código.
- La sincronización copia todos los datos: con dos celulares a la vez, ante un conflicto gana la copia más reciente.
- Si se olvida la contraseña y no se tiene el celular original, la copia del servidor no puede descifrarse (costo del modelo zero-knowledge).
- La recuperación de contraseña necesita SMTP configurado en el backend.
- Falta probar: huella/PIN en un celular real, TalkBack y texto al 200 %, Android 8, rendimiento con 500 pacientes y 5.000 turnos, APK release firmado, y volver a correr los tests instrumentados.

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/…Entrega final.docx`](./docs/) | Documento de la entrega final: preentrega + secciones 19 a 33 de la Etapa 2 |
| [`docs/…Preentrega.md`](./docs/) | Preentrega aprobada (09/10/2026) |
| [`docs/TPOA_2026.pdf`](./docs/TPOA_2026.pdf) | Consigna del TPO |
| [`TPO1_app/IMPLEMENTACION.md`](./TPO1_app/IMPLEMENTACION.md) | Documentación técnica de la app: pantallas, arquitectura, datos, seguridad, tests |
| [`TPO1_backend/README.md`](./TPO1_backend/README.md) | API, ejecución con Docker y despliegue en Vercel |

## Equipo y forma de trabajo

Federico Vargas, Francisco Guerrero y Mateo Zanzottera. Docentes: Adrian Alberto Narducci y Alejandro Francisco Peña.

`main` estable; cada cambio entra por una rama `feature/*` y un pull request (#1 a #11 hasta la entrega final).

---

# 👋 Para el equipo

## Link a Figma para diseño de los TPO

Link para TPO1_app:
https://www.figma.com/design/rgXvOl5ZgXYwsMAkkrWbtS/Wireframe-Problema-TPO-agenda-medica?node-id=1-49&p=f&t=NSO5FcfrnPh9sFEB-0

## Link a Trello para el seguimiento de las tareas requeridas

🗂️ **Tablero TPO1_app:**

## 🔴 Lo que hay que saber ya

| | |
|---|---|
| **Cursada** | Viernes 18:45 a 22:15 |
| **1° Parcial** | Viernes **18/9** |
| **Clase remota sincrónica** | **Sábado 26/9, 9 a 13 hs** ⚠️ fuera del horario habitual |
| **2° Parcial** | Viernes **13/11** |
| **Recuperatorio y final adelantado** | Viernes **27/11** |
| **Cierre de notas y regularidades. Devolución de trabajos prácticos** | Viernes **04/12** |
| **Final regular** | Viernes **11/12** |
