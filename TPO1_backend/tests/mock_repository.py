"""
MockRepository del backend: reemplaza a MongoDB (Motor) por colecciones en memoria.

Los routers reciben la base con `Depends(get_database)`. En los tests se pisa esa dependencia con
`app.dependency_overrides[get_database]`, así la API corre completa (validación, JWT, hashing,
aislamiento por tenant) pero sin Mongo, sin red y sin Atlas.

Implementa solo lo que usan los routers: find_one, insert_one, update_one ($set y upsert) y
create_index. Para simular que la base se cae, se pone `fallar = True`.
"""

import copy
from typing import Any, Dict, List, Optional


class FallaSimuladaDeBase(Exception):
    """Lo que lanzaría Motor si Atlas no responde (ServerSelectionTimeoutError)."""


def _coincide(documento: Dict[str, Any], filtro: Dict[str, Any]) -> bool:
    return all(documento.get(campo) == valor for campo, valor in filtro.items())


class _Resultado:
    def __init__(self, **campos):
        self.__dict__.update(campos)


class MockCollection:
    def __init__(self, base: "MockDatabase", nombre: str):
        self._base = base
        self.nombre = nombre
        self.documentos: List[Dict[str, Any]] = []
        self.unicos: List[str] = []

    async def find_one(self, filtro: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        self._base._registrar(f"{self.nombre}.find_one")
        for doc in self.documentos:
            if _coincide(doc, filtro):
                return copy.deepcopy(doc)
        return None

    async def insert_one(self, documento: Dict[str, Any]):
        self._base._registrar(f"{self.nombre}.insert_one")
        for campo in self.unicos:
            if any(d.get(campo) == documento.get(campo) for d in self.documentos):
                raise ValueError(f"Clave duplicada en {self.nombre}.{campo}")
        nuevo = copy.deepcopy(documento)
        nuevo.setdefault("_id", f"{self.nombre}-{len(self.documentos) + 1}")
        self.documentos.append(nuevo)
        return _Resultado(inserted_id=nuevo["_id"])

    async def update_one(self, filtro: Dict[str, Any], cambios: Dict[str, Any], upsert: bool = False):
        self._base._registrar(f"{self.nombre}.update_one")
        valores = cambios.get("$set", {})
        for doc in self.documentos:
            if _coincide(doc, filtro):
                doc.update(copy.deepcopy(valores))
                return _Resultado(matched_count=1, modified_count=1, upserted_id=None)
        if upsert:
            resultado = await self.insert_one({**filtro, **valores})
            return _Resultado(matched_count=0, modified_count=0, upserted_id=resultado.inserted_id)
        return _Resultado(matched_count=0, modified_count=0, upserted_id=None)

    async def create_index(self, campos, unique: bool = False, **_):
        if unique and isinstance(campos, str):
            self.unicos.append(campos)
        return campos


class MockDatabase:
    """Se usa como `db["users"]`, igual que AsyncIOMotorDatabase."""

    def __init__(self):
        self._colecciones: Dict[str, MockCollection] = {}
        self.fallar = False
        self.operaciones: List[str] = []
        # Mismos índices únicos que crea app/core/database.py
        self["users"].unicos.extend(["email", "tenant_id"])

    def __getitem__(self, nombre: str) -> MockCollection:
        if nombre not in self._colecciones:
            self._colecciones[nombre] = MockCollection(self, nombre)
        return self._colecciones[nombre]

    def _registrar(self, operacion: str):
        if self.fallar:
            raise FallaSimuladaDeBase("La base de datos no responde")
        self.operaciones.append(operacion)
