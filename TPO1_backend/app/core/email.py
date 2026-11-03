import logging
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
import aiosmtplib
from app.core.config import settings

logger = logging.getLogger("uvicorn.error")


async def send_reset_password_email(email_to: str, token: str) -> bool:
    """
    Envía un correo con el token y enlace de recuperación de contraseña.
    Si SMTP no está configurado, loguea el token para permitir pruebas locales y evaluación.
    """
    reset_url = f"{settings.FRONTEND_RESET_PASSWORD_URL}?token={token}"
    
    subject = "Recuperación de Contraseña — AgendaSalud"
    body_text = f"""Hola,

Has solicitado restablecer tu contraseña en AgendaSalud.
Para continuar, utiliza el siguiente enlace o ingresa el token en tu aplicación:

Enlace: {reset_url}
Token: {token}

Este token expirará en {settings.PASSWORD_RESET_TOKEN_EXPIRE_MINUTES} minutos.
Si no solicitaste este cambio, puedes ignorar este correo de forma segura.

Atentamente,
Equipo de Soporte de AgendaSalud
"""

    body_html = f"""
    <html>
      <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
        <h2 style="color: #1976d2;">Recuperación de Contraseña</h2>
        <p>Hola,</p>
        <p>Has solicitado restablecer tu contraseña en <strong>AgendaSalud</strong>.</p>
        <p>Para definir una nueva contraseña, haz clic en el siguiente botón o utiliza el token provisto:</p>
        <p style="margin: 25px 0;">
          <a href="{reset_url}" style="background-color: #1976d2; color: #fff; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold;">
            Restablecer Contraseña
          </a>
        </p>
        <p><strong>Token:</strong> <code style="background-color: #f4f4f4; padding: 4px 8px; border-radius: 4px;">{token}</code></p>
        <p style="font-size: 0.9em; color: #777;">Este token expirará en {settings.PASSWORD_RESET_TOKEN_EXPIRE_MINUTES} minutos.</p>
        <p style="font-size: 0.85em; color: #999;">Si no solicitaste este cambio, desestima este mensaje.</p>
      </body>
    </html>
    """

    # Si SMTP no está configurado, registrar en logs para desarrollo/demo
    if not settings.SMTP_HOST or not settings.SMTP_USER:
        logger.warning(
            f"[DEMO/DEV MODE] SMTP no configurado. Token de recuperación para {email_to}:\n"
            f"--> URL: {reset_url}\n"
            f"--> TOKEN: {token}"
        )
        return True

    try:
        message = MIMEMultipart("alternative")
        message["From"] = f"{settings.SMTP_FROM_NAME} <{settings.SMTP_FROM_EMAIL}>"
        message["To"] = email_to
        message["Subject"] = subject

        message.attach(MIMEText(body_text, "plain", "utf-8"))
        message.attach(MIMEText(body_html, "html", "utf-8"))

        await aiosmtplib.send(
            message,
            hostname=settings.SMTP_HOST,
            port=settings.SMTP_PORT,
            username=settings.SMTP_USER,
            password=settings.SMTP_PASSWORD,
            start_tls=True if settings.SMTP_PORT == 587 else False
        )
        logger.info(f"Correo de recuperación enviado exitosamente a {email_to}")
        return True
    except Exception as e:
        logger.error(f"Error al enviar correo de recuperación a {email_to}: {e}")
        return False

