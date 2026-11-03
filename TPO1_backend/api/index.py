import os
import sys
from contextlib import asynccontextmanager

# Asegurar que el directorio raíz de TPO1_backend esté en el PYTHONPATH
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.core.config import settings
from app.core.database import close_database
from app.routers import auth, sync


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Inicialización de recursos (si fuera necesario)
    yield
    # Limpieza de conexiones en shutdown
    await close_database()


app = FastAPI(
    title=settings.PROJECT_NAME,
    description="Backend Serverless para persistencia y sincronización segura (Zero-Knowledge) de AgendaSalud",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
    lifespan=lifespan
)

# Configuración de CORS para permitir peticiones desde la app móvil y herramientas de test
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Inclusión de routers modulares
app.include_router(auth.router, prefix=settings.API_V1_STR)
app.include_router(sync.router, prefix=settings.API_V1_STR)


@app.get("/", tags=["Estado y Diagnóstico"])
async def root():
    return {
        "status": "online",
        "service": settings.PROJECT_NAME,
        "environment": settings.ENVIRONMENT,
        "docs": "/docs",
        "endpoints": {
            "auth": f"{settings.API_V1_STR}/auth",
            "sync": f"{settings.API_V1_STR}/sync"
        }
    }


@app.get("/health", tags=["Estado y Diagnóstico"])
async def health_check():
    return {"status": "healthy", "timestamp": os.getenv("VERCEL_REGION", "local")}

