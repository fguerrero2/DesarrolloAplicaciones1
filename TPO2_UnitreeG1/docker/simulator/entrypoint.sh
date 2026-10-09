#!/bin/bash
set -e

export DISPLAY=:1

# Si el contenedor se reinicia (en vez de recrearse) quedan los locks de la
# corrida anterior y Xvfb aborta con "Server is already active for display 1".
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1

Xvfb "$DISPLAY" -screen 0 1280x800x24 &

for i in $(seq 1 20); do
    if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
        break
    fi
    sleep 0.5
done

fluxbox >/tmp/fluxbox.log 2>&1 &

x11vnc -display "$DISPLAY" -forever -shared -nopw -rfbport 5900 -bg -o /tmp/x11vnc.log

websockify --web=/usr/share/novnc/ 6080 localhost:5900 &

if [ ! -d /workspace/unitree_mujoco/simulate_python ]; then
    echo "ERROR: /workspace/unitree_mujoco no está montado (revisá el volumen en docker-compose.yml)." >&2
    exit 1
fi

cd /workspace/unitree_mujoco/simulate_python
exec python3 unitree_mujoco.py
