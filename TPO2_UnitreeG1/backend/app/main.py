from datetime import datetime, timezone
from enum import Enum
from threading import Lock

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field


class RobotMode(str, Enum):
    idle = "idle"
    moving = "moving"
    stopped = "stopped"


class Position(BaseModel):
    x: float = 0.0
    y: float = 0.0


class Area(BaseModel):
    width: float = Field(gt=0, description="Area width in meters")
    length: float = Field(gt=0, description="Area length in meters")


class MoveCommand(BaseModel):
    x: float = Field(description="Target X coordinate in meters")
    y: float = Field(description="Target Y coordinate in meters")


class RobotState(BaseModel):
    robot: str = "unitree-g1"
    mode: RobotMode
    position: Position
    area: Area
    simulator: str = "mock"
    updated_at: datetime


class MockG1Adapter:
    """Small local adapter that can later be replaced by Unitree SDK2/DDS."""

    def __init__(self) -> None:
        self._lock = Lock()
        self._area = Area(width=6, length=8)
        self._position = Position()
        self._mode = RobotMode.idle

    def state(self) -> RobotState:
        with self._lock:
            return RobotState(
                mode=self._mode,
                position=self._position,
                area=self._area,
                updated_at=datetime.now(timezone.utc),
            )

    def set_area(self, area: Area) -> RobotState:
        with self._lock:
            self._area = area
            self._position = Position()
            self._mode = RobotMode.idle
        return self.state()

    def move_to(self, target: MoveCommand) -> RobotState:
        with self._lock:
            if not (0 <= target.x <= self._area.width):
                raise ValueError("x must be inside the configured area")
            if not (0 <= target.y <= self._area.length):
                raise ValueError("y must be inside the configured area")
            self._position = Position(x=target.x, y=target.y)
            self._mode = RobotMode.moving
        return self.state()

    def stop(self) -> RobotState:
        with self._lock:
            self._mode = RobotMode.stopped
        return self.state()


app = FastAPI(
    title="Unitree G1 Digital Twin API",
    version="0.1.0",
    description="REST API for the TPO2 Unitree G1 simulator bridge.",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
adapter = MockG1Adapter()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok", "service": "unitree-g1-backend"}


@app.get("/api/v1/robot", response_model=RobotState)
def get_robot_state() -> RobotState:
    return adapter.state()


@app.put("/api/v1/area", response_model=RobotState)
def configure_area(area: Area) -> RobotState:
    return adapter.set_area(area)


@app.post("/api/v1/robot/move", response_model=RobotState)
def move_robot(command: MoveCommand) -> RobotState:
    try:
        return adapter.move_to(command)
    except ValueError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


@app.post("/api/v1/robot/stop", response_model=RobotState)
def stop_robot() -> RobotState:
    return adapter.stop()