import logging
from typing import Optional
from motor.motor_asyncio import AsyncIOMotorClient, AsyncIOMotorDatabase
from app.core.config import settings

logger = logging.getLogger("uvicorn.error")

class Database:
    client: Optional[AsyncIOMotorClient] = None
    db: Optional[AsyncIOMotorDatabase] = None

db_instance = Database()


async def get_database() -> AsyncIOMotorDatabase:
    """
    Obtiene la instancia de la base de datos MongoDB Atlas.
    Reutiliza la conexión del pool en entornos serverless (Vercel) o contenedores Docker.
    """
    if db_instance.db is None:
        client = None
        try:
            logger.info("Iniciando conexión a MongoDB...")
            client = AsyncIOMotorClient(
                settings.MONGODB_URI,
                maxPoolSize=10,
                minPoolSize=1,
                serverSelectionTimeoutMS=5000
            )
            db = client[settings.MONGODB_DB_NAME]
            
            # Ping para verificar conectividad
            await client.admin.command('ping')
            
            # Creación de índices necesarios para aislamiento Multi-Tenant
            await db["users"].create_index("email", unique=True)
            await db["users"].create_index("tenant_id", unique=True)
            await db["sync_snapshots"].create_index([("tenant_id", 1), ("version", -1)])
            
            db_instance.client = client
            db_instance.db = db
            logger.info("Conexión a MongoDB establecida con éxito.")
        except Exception as e:
            logger.error(f"Error al conectar con MongoDB: {e}")
            if client:
                client.close()
            db_instance.client = None
            db_instance.db = None
            raise e
            
    return db_instance.db


async def close_database():
    """Cierra la conexión al apagar el servicio."""
    if db_instance.client:
        db_instance.client.close()
        db_instance.client = None
        db_instance.db = None
        logger.info("Conexión a MongoDB Atlas cerrada.")

