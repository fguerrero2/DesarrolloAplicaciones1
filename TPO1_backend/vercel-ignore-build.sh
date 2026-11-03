#!/bin/bash
# ==============================================================================
# Script para "Ignored Build Step" en Vercel (Monorepo AgendaSalud)
# 
# Reglas de Vercel:
#   Exit code 1 -> CONTINUAR con el build y despliegue.
#   Exit code 0 -> CANCELAR el build (no hubo cambios relevantes, no consume tiempo).
# ==============================================================================

echo "=== [Vercel Ignored Build Step Check] ==="

# Si no hay commit previo definido por Vercel (primer despliegue o rebuild manual)
if [ -z "$VERCEL_GIT_PREVIOUS_SHA" ]; then
  echo "ℹ️ Primer despliegue o commit inicial detectado. Procediendo con el build..."
  exit 1
fi

echo "Comparando cambios entre $VERCEL_GIT_PREVIOUS_SHA y $VERCEL_GIT_COMMIT_SHA en TPO1_backend/..."

# Comparamos si hubo cambios dentro de la carpeta actual (.)
git diff --quiet "$VERCEL_GIT_PREVIOUS_SHA" "$VERCEL_GIT_COMMIT_SHA" .

DIFF_EXIT_CODE=$?

if [ $DIFF_EXIT_CODE -eq 0 ]; then
  echo "🛑 [SKIP BUILD] No se detectaron modificaciones en 'TPO1_backend/'."
  echo "ℹ️ El commit o merge afectó únicamente a la app móvil ('TPO1_app/') u otras carpetas."
  exit 0
else
  echo "🚀 [PROCEED BUILD] Se detectaron cambios en 'TPO1_backend/'. Desplegando en Vercel..."
  exit 1
fi

