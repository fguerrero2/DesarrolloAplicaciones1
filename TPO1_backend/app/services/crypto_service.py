import base64
import os
from typing import Tuple, Dict, Any
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes


class CryptoService:
    """
    Servicio de utilidades criptográficas para Zero-Knowledge y Envelope Encryption.
    Útil para pruebas locales, scripts de test y referencia para la app móvil Android.
    """

    @staticmethod
    def derive_key(password: str, salt: bytes, iterations: int = 120_000) -> bytes:
        """Deriva una clave simétrica de 256 bits (32 bytes) a partir de una contraseña y salt."""
        kdf = PBKDF2HMAC(
            algorithm=hashes.SHA256(),
            length=32,
            salt=salt,
            iterations=iterations,
        )
        return kdf.derive(password.encode("utf-8"))

    @staticmethod
    def generate_random_bytes(length: int = 32) -> bytes:
        """Genera bytes aleatorios criptográficamente seguros."""
        return os.urandom(length)

    @classmethod
    def wrap_key(cls, kek: bytes, dek: bytes) -> Tuple[str, str, str]:
        """
        Cifra la clave de datos (DEK) usando la clave de envoltura (KEK) con AES-256-GCM.
        Devuelve (encrypted_dek_b64, iv_b64, tag_b64).
        """
        aesgcm = AESGCM(kek)
        iv = cls.generate_random_bytes(12)  # 12 bytes estándar para GCM
        # AESGCM.encrypt en cryptography concatena ciphertext + 16 bytes auth tag al final
        encrypted = aesgcm.encrypt(iv, dek, None)
        ciphertext = encrypted[:-16]
        tag = encrypted[-16:]

        return (
            base64.b64encode(ciphertext).decode("utf-8"),
            base64.b64encode(iv).decode("utf-8"),
            base64.b64encode(tag).decode("utf-8")
        )

    @classmethod
    def unwrap_key(cls, kek: bytes, encrypted_dek_b64: str, iv_b64: str, tag_b64: str) -> bytes:
        """
        Descifra la clave de datos (DEK) a partir de la KEK y el sobre digital.
        """
        ciphertext = base64.b64decode(encrypted_dek_b64)
        iv = base64.b64decode(iv_b64)
        tag = base64.b64decode(tag_b64)

        aesgcm = AESGCM(kek)
        # Recomponemos ciphertext + tag
        data_to_decrypt = ciphertext + tag
        return aesgcm.decrypt(iv, data_to_decrypt, None)

    @classmethod
    def encrypt_payload(cls, dek: bytes, plaintext: str) -> Dict[str, str]:
        """
        Cifra un payload JSON/texto con la clave DEK usando AES-256-GCM.
        """
        aesgcm = AESGCM(dek)
        iv = cls.generate_random_bytes(12)
        encrypted = aesgcm.encrypt(iv, plaintext.encode("utf-8"), None)
        ciphertext = encrypted[:-16]
        tag = encrypted[-16:]

        return {
            "ciphertext": base64.b64encode(ciphertext).decode("utf-8"),
            "iv": base64.b64encode(iv).decode("utf-8"),
            "auth_tag": base64.b64encode(tag).decode("utf-8")
        }

    @classmethod
    def decrypt_payload(cls, dek: bytes, ciphertext_b64: str, iv_b64: str, tag_b64: str) -> str:
        """
        Descifra un payload cifrado con la DEK.
        """
        ciphertext = base64.b64decode(ciphertext_b64)
        iv = base64.b64decode(iv_b64)
        tag = base64.b64decode(tag_b64)

        aesgcm = AESGCM(dek)
        decrypted = aesgcm.decrypt(iv, ciphertext + tag, None)
        return decrypted.decode("utf-8")

