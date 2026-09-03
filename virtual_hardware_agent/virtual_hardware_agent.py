"""
Virtual Hardware Agent — KrishiTech Field Node Simulator
=========================================================
Simulates the proposed hardware field unit for SIH PS-26180 demo.

Component A: BLE GATT server (sensor array + power system telemetry)
Component B: FastAPI camera module (serves rotating sample leaf images)

The phone's on-device TFLite AI is the compute unit — this agent NEVER
runs inference.  It only produces sensor readings, battery telemetry,
and raw camera images.

Usage:
    pip install -r requirements.txt
    python virtual_hardware_agent.py
"""

from __future__ import annotations

import asyncio
import logging
import math
import os
import random
import shutil
import struct
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional

# ---------------------------------------------------------------------------
# Logging
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  [%(name)s]  %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("FieldNode")

# ---------------------------------------------------------------------------
# UUID Spec — must stay in sync with Android SensorBleManager.kt BleSpec
# ---------------------------------------------------------------------------
SERVICE_UUID            = "0000181A-0000-1000-8000-00805F9B34FB"
SOIL_MOISTURE_CHAR_UUID = "00002A6F-0000-1000-8000-00805F9B34FB"
TEMPERATURE_CHAR_UUID   = "00002A6E-0000-1000-8000-00805F9B34FB"
HUMIDITY_CHAR_UUID       = "00002A6D-0000-1000-8000-00805F9B34FB"
RAINFALL_CHAR_UUID       = "0000FF01-0000-1000-8000-00805F9B34FB"
BATTERY_LEVEL_CHAR_UUID  = "00002A19-0000-1000-8000-00805F9B34FB"

DEVICE_NAME = "KrishiTech-FieldNode"

# ---------------------------------------------------------------------------
# Paths (relative to this script's directory)
# ---------------------------------------------------------------------------
BASE_DIR     = Path(__file__).resolve().parent
SAMPLE_DIR   = BASE_DIR / "sample_images"
STATIC_DIR   = BASE_DIR / "static"
LATEST_IMAGE = STATIC_DIR / "latest.jpg"

# Shared in-memory telemetry state (exposed via BLE and HTTP /telemetry)
_telemetry_data: Dict[str, Any] = {
    "soil_moisture": 45.0,
    "temperature": 27.5,
    "humidity": 55.0,
    "raining": False,
    "battery": 50.0,
}

# ═══════════════════════════════════════════════════════════════════════════
#  COMPONENT A — BLE Sensor Array + Power System Simulator
# ═══════════════════════════════════════════════════════════════════════════

def _clamp(val: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, val))


def _random_walk(current: float, lo: float, hi: float, step: float = 1.5) -> float:
    """Small random-walk drift, clamped to [lo, hi]."""
    return _clamp(current + random.uniform(-step, step), lo, hi)


def _float_to_le_bytes(val: float) -> bytes:
    """Pack a float as 4-byte little-endian — matches the Android app's byte parser."""
    return struct.pack("<f", val)


async def run_ble_server() -> None:
    """
    Simulates live sensor array + power system telemetry.
    If 'bless' is available and supported, broadcasts via BLE GATT.
    Regardless of BLE status, continuously updates live telemetry every 3 seconds.
    """
    server = None
    try:
        from bless import (  # type: ignore[import-untyped]
            BlessGATTCharacteristic,
            BlessServer,
            GATTAttributePermissions,
            GATTCharacteristicProperties,
        )

        def on_read(characteristic: BlessGATTCharacteristic, **kwargs: Any) -> bytearray:
            return characteristic.value  # type: ignore[return-value]

        def on_write(characteristic: BlessGATTCharacteristic, value: Any, **kwargs: Any) -> None:
            pass

        server = BlessServer(name=DEVICE_NAME, loop=asyncio.get_event_loop())
        server.read_request_func  = on_read
        server.write_request_func = on_write

        await server.add_new_service(SERVICE_UUID)

        char_flags = (
            GATTCharacteristicProperties.read
            | GATTCharacteristicProperties.notify
        )
        char_perms = GATTAttributePermissions.readable

        for uuid in [
            SOIL_MOISTURE_CHAR_UUID,
            TEMPERATURE_CHAR_UUID,
            HUMIDITY_CHAR_UUID,
            RAINFALL_CHAR_UUID,
            BATTERY_LEVEL_CHAR_UUID,
        ]:
            await server.add_new_characteristic(
                SERVICE_UUID, uuid, char_flags, None, char_perms,
            )

        await server.start()
        log.info("BLE GATT server started — advertising as '%s'", DEVICE_NAME)
    except Exception as exc:
        log.info("BLE peripheral skipped (running in Software Telemetry mode): %s", exc)
        server = None


    # ── Sensor state — initial random values within realistic ranges ──────
    soil_moisture: float = random.uniform(30.0, 60.0)
    temperature:   float = random.uniform(22.0, 32.0)
    humidity:      float = random.uniform(40.0, 70.0)
    raining:       int   = 0
    battery:       float = 98.4  # Start near full charge (100%)

    tick = 0

    while True:
        # Random-walk each sensor
        soil_moisture = _random_walk(soil_moisture, 10.0, 90.0, step=1.2)
        temperature   = _random_walk(temperature,   15.0, 45.0, step=0.4)
        humidity       = _random_walk(humidity,       20.0, 95.0, step=1.0)
        raining        = 1 if random.random() < 0.05 else 0

        # Battery: starts near 100%, drains very slowly (~0.05% every 3s)
        # with solar trickle charge maintaining it high
        battery = _clamp(battery - random.uniform(0.02, 0.06), 85.0, 100.0)
        tick += 1

        # Store in global telemetry for HTTP API
        _telemetry_data["soil_moisture"] = round(soil_moisture, 1)
        _telemetry_data["temperature"] = round(temperature, 1)
        _telemetry_data["humidity"] = round(humidity, 1)
        _telemetry_data["raining"] = bool(raining)
        _telemetry_data["battery"] = round(battery, 1)

        # Update BLE characteristics if server is running
        if server is not None:
            try:
                server.get_characteristic(SOIL_MOISTURE_CHAR_UUID).value = bytearray(
                    _float_to_le_bytes(soil_moisture)
                )
                server.get_characteristic(TEMPERATURE_CHAR_UUID).value = bytearray(
                    _float_to_le_bytes(temperature)
                )
                server.get_characteristic(HUMIDITY_CHAR_UUID).value = bytearray(
                    _float_to_le_bytes(humidity)
                )
                server.get_characteristic(RAINFALL_CHAR_UUID).value = bytearray(
                    [raining]
                )
                server.get_characteristic(BATTERY_LEVEL_CHAR_UUID).value = bytearray(
                    _float_to_le_bytes(battery)
                )

                for uuid in [
                    SOIL_MOISTURE_CHAR_UUID,
                    TEMPERATURE_CHAR_UUID,
                    HUMIDITY_CHAR_UUID,
                    RAINFALL_CHAR_UUID,
                    BATTERY_LEVEL_CHAR_UUID,
                ]:
                    server.update_value(SERVICE_UUID, uuid)
            except Exception as e:
                log.debug("BLE update skipped: %s", e)

        # Console output — clearly labelled by component
        log.info(
            "Sensor array:  Moisture=%.1f%%  Temp=%.1f°C  Humidity=%.1f%%  Rain=%s",
            soil_moisture, temperature, humidity, "YES" if raining else "no",
        )
        log.info(
            "Power system:  Battery=%.1f%%  (solar trickle)",
            battery,
        )

        await asyncio.sleep(3)




# ═══════════════════════════════════════════════════════════════════════════
#  COMPONENT B — Virtual Camera Module (image server, NO AI)
# ═══════════════════════════════════════════════════════════════════════════

# In-memory metadata for the latest capture
_capture_meta: Dict[str, Any] = {
    "image_url": "",
    "crop_hint": "",
    "captured_at": 0,
}


def _discover_sample_images() -> List[Path]:
    """
    Returns list of image files in sample_images/ (including all subdirectories).
    Creates the directory + a README if it doesn't exist.
    """
    SAMPLE_DIR.mkdir(parents=True, exist_ok=True)

    # Search recursively for all common image formats
    valid_exts = {".jpg", ".jpeg", ".png", ".webp"}
    images = [
        p for p in SAMPLE_DIR.rglob("*")
        if p.is_file() and p.suffix.lower() in valid_exts
    ]

    # Shuffle so demo captures a random mix of classes/crops
    random.shuffle(images)

    if not images:
        readme = SAMPLE_DIR / "README.md"
        if not readme.exists():
            readme.write_text(
                "# Leaf & Pest Dataset Folder\n\n"
                "Place your Kaggle dataset or sample leaf/pest photos in this directory.\n"
                "Subfolders are supported automatically! (e.g. `Tomato___Early_blight/*.jpg`)\n",
                encoding="utf-8",
            )
        log.warning(
            "No images found in %s or subfolders — camera module will return empty responses "
            "until images are added.",
            SAMPLE_DIR,
        )
    else:
        log.info("Discovered %d dataset images across %s", len(images), SAMPLE_DIR)
    return images


def _parse_crop_hint(file_path: Path) -> str:
    """
    Extract crop name from either parent directory or filename prefix.
    Examples:
      - 'Tomato___Early_blight/image.jpg' -> 'Tomato'
      - 'Corn_(maize)___Common_rust/img.jpg' -> 'Corn_(maize)'
      - 'tomato_late_blight_1.jpg' -> 'tomato'
    """
    parent_name = file_path.parent.name
    if parent_name and parent_name != SAMPLE_DIR.name:
        if "___" in parent_name:
            return parent_name.split("___")[0]
        parts = parent_name.split("_")
        if parts:
            return parts[0]

    stem = file_path.stem
    if "___" in stem:
        return stem.split("___")[0]
    parts = stem.split("_")
    return parts[0] if parts else "unknown"


async def _image_rotation_loop(images: List[Path]) -> None:
    """Every 15 s, copy the next image to static/latest.jpg and update metadata."""
    STATIC_DIR.mkdir(parents=True, exist_ok=True)
    idx = 0

    while True:
        if images:
            src = images[idx % len(images)]
            shutil.copy2(str(src), str(LATEST_IMAGE))

            ts = int(time.time())
            crop = _parse_crop_hint(src)

            _capture_meta["image_url"]   = f"/static/latest.jpg?t={ts}"
            _capture_meta["crop_hint"]   = crop
            _capture_meta["captured_at"] = ts

            log.info(
                "Camera module:  Captured '%s' → crop_hint='%s'",
                src.name, crop,
            )
            idx += 1

        await asyncio.sleep(15)


async def run_camera_server() -> None:
    """FastAPI image server — serves raw captures, NEVER runs inference."""
    from fastapi import FastAPI
    from fastapi.responses import JSONResponse
    from fastapi.staticfiles import StaticFiles
    import uvicorn

    app = FastAPI(title="KrishiTech Virtual Camera Module")

    STATIC_DIR.mkdir(parents=True, exist_ok=True)
    app.mount("/static", StaticFiles(directory=str(STATIC_DIR)), name="static")

    from fastapi.responses import HTMLResponse

    TERMINAL_HTML = STATIC_DIR / "terminal.html"

    @app.get("/", response_class=HTMLResponse)
    async def index() -> HTMLResponse:
        if TERMINAL_HTML.exists():
            return HTMLResponse(content=TERMINAL_HTML.read_text(encoding="utf-8"))
        return HTMLResponse(content="<h1>Terminal HTML missing</h1>")

    @app.get("/latest-capture")
    async def latest_capture() -> JSONResponse:
        return JSONResponse(_capture_meta)

    @app.get("/telemetry")
    async def telemetry() -> JSONResponse:
        return JSONResponse({
            "sensor_array": {
                "soil_moisture_percent": _telemetry_data.get("soil_moisture"),
                "temperature_celsius": _telemetry_data.get("temperature"),
                "humidity_percent": _telemetry_data.get("humidity"),
                "raining": _telemetry_data.get("raining"),
            },
            "power_system": {
                "battery_level_percent": _telemetry_data.get("battery"),
            }
        })

    images = _discover_sample_images()

    # Run the image rotation loop and the uvicorn server concurrently
    config = uvicorn.Config(
        app, host="0.0.0.0", port=5000, log_level="info",
    )
    server = uvicorn.Server(config)

    await asyncio.gather(
        _image_rotation_loop(images),
        server.serve(),
    )


# ═══════════════════════════════════════════════════════════════════════════
#  MAIN — run both components concurrently
# ═══════════════════════════════════════════════════════════════════════════

async def main() -> None:
    log.info("=" * 60)
    log.info("  KrishiTech Virtual Field Node — starting up")
    log.info("  BLE device name : %s", DEVICE_NAME)
    log.info("  Camera endpoint : http://0.0.0.0:5000/latest-capture")
    log.info("  NOTE: This agent simulates sensors, camera & power ONLY.")
    log.info("        AI inference runs on the phone (TFLite), NOT here.")
    log.info("=" * 60)

    await asyncio.gather(
        run_ble_server(),
        run_camera_server(),
    )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        log.info("Shutting down Virtual Field Node.")
        sys.exit(0)
