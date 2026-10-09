# Backend Unitree G1

API REST inicial para controlar la posición del gemelo digital. Arranca con un adaptador local (`mock`) para poder desarrollar la app Android sin tener MuJoCo, DDS o el robot físico conectados.

## Ejecutar

```bash
cd backend
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

Documentación interactiva: `http://127.0.0.1:8000/docs`

## Endpoints

- `GET /health`: disponibilidad del servicio.
- `GET /api/v1/robot`: estado actual del G1.
- `PUT /api/v1/area`: configura ancho y largo en metros.
- `POST /api/v1/robot/move`: mueve el mock a `{ "x": 2, "y": 5 }`.
- `POST /api/v1/robot/stop`: detiene el movimiento.

## Gemelo digital oficial

El modelo y las escenas MJCF se obtienen desde el repositorio oficial de Unitree:

- [unitree_mujoco](https://github.com/unitreerobotics/unitree_mujoco) - soporte para `g1`, simulador Python/C++ y escenas del robot.
- [unitree_sdk2_python](https://github.com/unitreerobotics/unitree_sdk2_python) - interfaz Python para DDS y control de alto nivel.

Ambos repositorios declaran licencia BSD-3-Clause. La integración real reemplazará `MockG1Adapter` por un adaptador que publique/lea mensajes `unitree_hg` sobre DDS, manteniendo estos endpoints para la app Android.

## Simulador en Docker

El repo `unitree_mujoco` debe estar clonado como carpeta **hermana** de este proyecto (mismo nivel que `TPO2_UnitreeG1`), porque `docker-compose.yml` lo monta como volumen desde `../../unitree_mujoco`:

```
ghrepo/
├── DesarrolloAplicaciones1/TPO2_UnitreeG1/   # este repo
└── unitree_mujoco/                            # https://github.com/unitreerobotics/unitree_mujoco
```

Desde la raíz de `TPO2_UnitreeG1`:

```bash
docker compose up --build
```

Esto levanta dos servicios:
- **simulator**: imagen con `mujoco`, `unitree_sdk2_python` (compilando CycloneDDS) y un Xvfb + x11vnc + noVNC para ver el visor sin instalar nada en el host. Abrí `http://localhost:6080/vnc.html` y conectate (sin password) para ver al G1.
- **backend**: la API FastAPI, corriendo con `network_mode: "service:simulator"` para compartir el loopback (`lo`) con el simulador, tal como espera `unitree_sdk2_python` (`INTERFACE = "lo"` en `simulate_python/config.py`). Queda disponible en `http://localhost:8000/docs`.

La config del simulador (`unitree_mujoco/simulate_python/config.py`) ya está seteada en `ROBOT = "g1"` y `USE_JOYSTICK = 0` (no hay gamepad físico dentro del contenedor).

Para parar todo: `docker compose down`.