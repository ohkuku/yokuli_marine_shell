#!/usr/bin/env python3
"""Validate content-addressed Stage 11 renderer candidates without approving them."""

from __future__ import annotations

import hashlib
import json
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "docs/reference/wp8/golden/GOLDEN_CANDIDATES.json"
COMPARISON = ROOT / "docs/reference/wp8/artifacts/STAGE11_REFERENCE_COMPARISON.json"
MEASUREMENTS = ROOT / "docs/reference/wp8/WP8_REFERENCE_MEASUREMENTS.json"
EXPECTED_HASH = "af4ed6d799997ddb973d6795eec6905bf9757b22745d462f4313d9e2620d4ba5"
REQUIRED_SCENES = {
    "wp8_start_360_dark",
    "wp8_start_360_light",
    "wp8_start_320_square",
    "wp8_all_apps_360",
    "wp8_edit_medium",
    "wp8_context_menu",
    "wp8_alphabet_jump",
    "wp8_tile_launch_plane",
    "wp8_start_360_square",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Stage 11 fidelity validation failed: {message}")


def png_dimensions(payload: bytes) -> tuple[int, int]:
    require(payload.startswith(b"\x89PNG\r\n\x1a\n"), "candidate is not a PNG")
    require(payload[12:16] == b"IHDR", "candidate PNG has no IHDR")
    return struct.unpack(">II", payload[16:24])


def main() -> int:
    manifest = json.loads(MANIFEST.read_text())
    comparison = json.loads(COMPARISON.read_text())
    measurements = json.loads(MEASUREMENTS.read_text())

    require(manifest["status"] == "CANDIDATE_PENDING_HUMAN_REVIEW", "candidate status must remain pending")
    require(manifest["reviewedMeasurementHash"] == EXPECTED_HASH, "candidate reference hash drift")
    require(measurements["review"]["reviewedMeasurementHash"] == EXPECTED_HASH, "approved reference hash drift")
    require(measurements["review"]["decision"] == "APPROVED", "Stage 2.5 reference is not owner-approved")
    require("reviewedBy" not in manifest, "Codex must not self-approve Golden candidates")

    seen: set[str] = set()
    for capture in manifest["captures"]:
        scene = capture["sceneId"]
        require(scene not in seen, f"duplicate scene {scene}")
        seen.add(scene)
        relative = Path(capture["path"])
        require(not relative.is_absolute() and ".." not in relative.parts, f"unsafe path for {scene}")
        path = (ROOT / relative).resolve()
        require(path.is_relative_to(ROOT.resolve()), f"candidate escapes repository: {scene}")
        payload = path.read_bytes()
        require(hashlib.sha256(payload).hexdigest() == capture["sha256"], f"SHA-256 mismatch for {scene}")
        require(len(payload) == capture["byteSize"], f"byte size mismatch for {scene}")
        width, height = png_dimensions(payload)
        require(width == capture["dimensions"]["widthPx"], f"width mismatch for {scene}")
        require(height == capture["dimensions"]["heightPx"], f"height mismatch for {scene}")
        require(capture["approvalStatus"] == "PENDING_HUMAN_REVIEW", f"premature approval for {scene}")

    require(REQUIRED_SCENES == seen, "candidate set must match the eight Master scenes plus 360 square")
    require(comparison["status"] == "CANDIDATE_PENDING_HUMAN_REVIEW", "comparison status must remain pending")
    require(comparison["reviewedMeasurementHash"] == EXPECTED_HASH, "comparison reference hash drift")
    require(set(comparison["candidateSceneIds"]) == seen, "comparison and candidate manifest disagree")
    require(comparison["physicalDeviceClaim"] == "UNVERIFIED_HARDWARE", "comparison invents physical evidence")

    print(f"GOLDEN_CANDIDATE_VALIDATION=PASS captures={len(seen)} status={manifest['status']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
