FROM python:3.11-slim

ENV DEBIAN_FRONTEND=noninteractive \
    PIP_NO_CACHE_DIR=1

# Build deps (cyclonedds may need to compile from source depending on arch)
# + runtime libs for MuJoCo's GLFW viewer running under a virtual X server
# + Xvfb/x11vnc/noVNC to expose that viewer through the browser.
RUN apt-get update && apt-get install -y --no-install-recommends \
    git cmake build-essential pkg-config libssl-dev python3-dev \
    libgl1 libglx-mesa0 libgl1-mesa-dri libglu1-mesa libglfw3 libosmesa6 \
    libegl1 libgles2 mesa-utils \
    xvfb x11vnc novnc websockify fluxbox x11-utils \
    && rm -rf /var/lib/apt/lists/*

RUN pip install mujoco pygame

# unitree_sdk2_python's cyclonedds pip package only ships the Python binding;
# it needs the CycloneDDS C library built and pointed to via CYCLONEDDS_HOME
# (this is the "Could not locate cyclonedds" error the upstream README warns about).
RUN git clone --depth 1 --branch releases/0.10.x https://github.com/eclipse-cyclonedds/cyclonedds.git /opt/cyclonedds-src \
    && cmake -B /opt/cyclonedds-src/build -S /opt/cyclonedds-src \
        -DCMAKE_INSTALL_PREFIX=/opt/cyclonedds -DBUILD_EXAMPLES=OFF -DBUILD_TESTING=OFF \
    && cmake --build /opt/cyclonedds-src/build --target install -j"$(nproc)" \
    && rm -rf /opt/cyclonedds-src

ENV CYCLONEDDS_HOME=/opt/cyclonedds \
    CMAKE_PREFIX_PATH=/opt/cyclonedds \
    LD_LIBRARY_PATH=/opt/cyclonedds/lib

# unitree_sdk2_python is not vendored in the unitree_mujoco repo, so it's
# fetched here at build time and installed in editable mode as upstream recommends.
RUN git clone --depth 1 https://github.com/unitreerobotics/unitree_sdk2_python.git /opt/unitree_sdk2_python \
    && pip install -e /opt/unitree_sdk2_python

# unitree_mujoco itself is bind-mounted at runtime (see docker-compose.yml),
# so local edits to the simulator/config are picked up without rebuilding the image.
WORKDIR /workspace/unitree_mujoco/simulate_python

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

EXPOSE 6080
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
