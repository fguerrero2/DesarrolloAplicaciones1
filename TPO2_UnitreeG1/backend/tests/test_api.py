from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_health() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_move_inside_area_updates_position() -> None:
    response = client.post("/api/v1/robot/move", json={"x": 2, "y": 5})
    assert response.status_code == 200
    assert response.json()["position"] == {"x": 2, "y": 5}
    assert response.json()["mode"] == "moving"


def test_move_outside_area_is_rejected() -> None:
    response = client.post("/api/v1/robot/move", json={"x": 7, "y": 5})
    assert response.status_code == 422