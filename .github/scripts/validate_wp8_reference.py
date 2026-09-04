#!/usr/bin/env python3
"""Semantic and content-address validation for the Stage 2.5 WP8 evidence."""

from __future__ import annotations

import argparse
from hashlib import sha256
import json
from pathlib import Path
import struct
import sys

from jsonschema import Draft202012Validator, FormatChecker


ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "docs/reference/wp8"
MEASUREMENTS = REFERENCE / "WP8_REFERENCE_MEASUREMENTS.json"
SCHEMA = REFERENCE / "WP8_REFERENCE_MEASUREMENTS.schema.json"
SOURCES = REFERENCE / "SOURCE_MANIFEST.json"
BASELINE_LOCK = ROOT / "docs/stages/stage-2.5/BASELINE_LOCK.json"

EXPECTED_COVERAGE = {
    "START",
    "ALL_APPS",
    "EDIT_MODE",
    "SLOW_PAGE_SWIPE",
    "FAST_FLING",
    "PIN",
    "LONG_PRESS_DRAG",
    "RESIZE",
    "UNPIN",
    "APP_OPEN_AND_BACK",
    "LIVE_TILE_CYCLE",
    "TILE_PRESS_FEEDBACK",
    "VIRTUAL_HARDWARE_KEYS",
}
CORE_MEASUREMENT_SCENARIOS = {
    "START_PRIMARY_GEOMETRY",
    "START_TO_ALL_APPS",
    "APP_OPEN_TRANSITION",
    "BACK_TO_START_TRANSITION",
    "LIVE_TILE_CYCLE",
}
ALLOWED_COVERAGE_STATUS = {"OBSERVED", "PARTIALLY_OBSERVED", "VISUAL_ONLY", "NOT_OBSERVED"}
FORBIDDEN_UNSUPPORTED_INTERACTIONS = {"TILE_PRESS", "LONG_PRESS_DRAG", "RESIZE", "UNPIN"}


class EvidenceError(ValueError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise EvidenceError(message)


def load_json(path: Path) -> dict:
    require(path.is_file(), f"missing JSON file: {path.relative_to(ROOT)}")
    return json.loads(path.read_text())


def repository_file(relative: str) -> Path:
    candidate = (ROOT / relative).resolve()
    try:
        candidate.relative_to(ROOT.resolve())
    except ValueError as error:
        raise EvidenceError(f"evidence path escapes repository: {relative}") from error
    require(candidate.is_file(), f"evidence file is missing: {relative}")
    require(not candidate.is_symlink(), f"evidence must not be a symlink: {relative}")
    return candidate


def file_sha256(path: Path) -> str:
    digest = sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def png_dimensions(path: Path) -> tuple[int, int]:
    with path.open("rb") as source:
        header = source.read(24)
    require(header[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG file: {path.relative_to(ROOT)}")
    require(header[12:16] == b"IHDR", f"PNG has no leading IHDR: {path.relative_to(ROOT)}")
    return struct.unpack(">II", header[16:24])


def validate_source_manifest(source: dict, measurements: dict) -> None:
    require(source.get("schemaVersion") == 1, "unsupported SOURCE_MANIFEST schemaVersion")
    require(source.get("profileId") == measurements["profileId"], "source and measurement profiles differ")

    recordings = source.get("recordings", [])
    require(len(recordings) == 1, "Stage 2.5 must have exactly one visual recording source")
    recording = recordings[0]
    require(recording["acquisitionKind"] == "EMULATOR_SCREEN_RECORDING", "visual source is not emulator output")
    require(recording["mediaType"] == "video/mp4", "recording must be MP4")
    require(recording["nominalFrameRateFps"] == 60, "recording must retain its measured 60 fps rate")
    require(recording["durationMillis"] == 408044, "recording duration drifted")
    require(recording["dimensions"] == {"widthPx": 1920, "heightPx": 1080}, "recording dimensions drifted")
    require(recording["exactOsBuild"] is None, "unknown OS build must not be invented")
    require(recording["exactEmulatorImage"] is None, "unknown emulator image must not be invented")
    require("repository owner" in recording["rightsBoundary"].lower(), "owner authorization boundary is missing")

    recording_path = repository_file(recording["path"])
    require(recording_path.stat().st_size == recording["byteSize"], "recording byte size mismatch")
    require(file_sha256(recording_path) == recording["sha256"], "recording SHA-256 mismatch")
    with recording_path.open("rb") as video:
        signature = video.read(12)
    require(signature[4:8] == b"ftyp", "recording has no ISO BMFF ftyp signature")

    capture_ids = {capture["id"] for capture in measurements["captures"]}
    extractions = source.get("extractions", [])
    extraction_ids = [item["captureId"] for item in extractions]
    require(len(extraction_ids) == len(set(extraction_ids)), "duplicate extraction captureId")
    require(set(extraction_ids) == capture_ids, "source extractions and measurement captures differ")
    recording_ids = {item["id"] for item in recordings}
    capture_by_id = {capture["id"]: capture for capture in measurements["captures"]}
    for extraction in extractions:
        capture = capture_by_id[extraction["captureId"]]
        require(extraction["sourceRecordingId"] in recording_ids, "extraction references unknown recording")
        require(extraction["visualTransform"] == "NONE", "reference frame was visually transformed")
        require(extraction["requestedTimestampMillis"] == extraction["frameTimestampMillis"], "decoder missed requested frame")
        require(extraction["frameTimestampMillis"] == capture["frameTimestampMillis"], "frame timestamp mismatch")
        require(extraction["extractionTool"] == ".github/scripts/extract_wp8_reference_frames.py", "untracked extraction tool")

    coverage_items = source.get("coverage", [])
    coverage_names = [item["scenario"] for item in coverage_items]
    require(len(coverage_names) == len(set(coverage_names)), "duplicate coverage scenario")
    require(set(coverage_names) == EXPECTED_COVERAGE, "reference coverage list is incomplete")
    coverage = {item["scenario"]: item["status"] for item in coverage_items}
    require(set(coverage.values()) <= ALLOWED_COVERAGE_STATUS, "unknown coverage status")
    for observed in ("START", "ALL_APPS", "APP_OPEN_AND_BACK", "LIVE_TILE_CYCLE"):
        require(coverage[observed] == "OBSERVED", f"core scenario not observed: {observed}")
    for absent in ("EDIT_MODE", "FAST_FLING", "PIN", "LONG_PRESS_DRAG", "RESIZE", "UNPIN", "TILE_PRESS_FEEDBACK"):
        require(coverage[absent] == "NOT_OBSERVED", f"unsupported scenario must remain NOT_OBSERVED: {absent}")
    require(coverage["VIRTUAL_HARDWARE_KEYS"] == "VISUAL_ONLY", "virtual key evidence must remain visual-only")

    documents = source.get("documents", [])
    require(len(documents) >= 4, "insufficient documentary corroboration")
    document_ids = [item["id"] for item in documents]
    require(len(document_ids) == len(set(document_ids)), "duplicate document id")
    for document in documents:
        require(document["url"].startswith("https://"), f"non-HTTPS document URL: {document['id']}")
        require("Microsoft" in document["publisher"], f"non-primary documentary source: {document['id']}")


def validate_capture_files(measurements: dict) -> None:
    captures = measurements.get("captures", [])
    ids = [capture["id"] for capture in captures]
    require(len(ids) == len(set(ids)), "duplicate capture id")
    require(len(captures) >= 8, "too few exact visual captures")
    for capture in captures:
        path = repository_file(capture["path"])
        require(capture["sourceType"] == "VIDEO_FRAME", f"capture is not a video frame: {capture['id']}")
        require(capture["mediaType"] == "image/png", f"capture is not lossless PNG: {capture['id']}")
        require(capture["isOriginal"] is False, f"decoded frame mislabeled as original: {capture['id']}")
        require(capture["isCropped"] is False, f"capture is cropped: {capture['id']}")
        require(path.stat().st_size == capture["byteSize"], f"capture byte size mismatch: {capture['id']}")
        require(file_sha256(path) == capture["sha256"], f"capture SHA-256 mismatch: {capture['id']}")
        width, height = png_dimensions(path)
        require(
            {"widthPx": width, "heightPx": height} == capture["dimensions"],
            f"capture dimensions mismatch: {capture['id']}",
        )


def validate_measurement_semantics(measurements: dict) -> None:
    captures = {capture["id"]: capture for capture in measurements["captures"]}
    sets = measurements["measurementSets"]
    set_ids = [item["id"] for item in sets]
    require(len(set_ids) == len(set(set_ids)), "duplicate measurement-set id")
    scenarios = {item["scenarioId"] for item in sets}
    require(CORE_MEASUREMENT_SCENARIOS <= scenarios, "core geometry or motion measurement is missing")

    interactions = {
        item["motionEvidence"]["interaction"]
        for item in sets
        if "motionEvidence" in item
    }
    require({"PAGE_SWIPE", "APP_OPEN", "BACK_RETURN", "LIVE_TILE_CYCLE"} <= interactions, "core motion interaction is missing")
    require(not (interactions & FORBIDDEN_UNSUPPORTED_INTERACTIONS), "recording gap was converted into fabricated motion evidence")

    for measurement in sets:
        require(measurement["viewport"]["widthPx"] == 480, "measurement viewport is not WP logical width")
        require(measurement["viewport"]["heightPx"] == 800, "measurement viewport is not WP logical height")
        require(measurement["viewport"]["densityDpi"] is None, "unobserved physical DPI must remain null")
        for capture_id in measurement["captureIds"]:
            require(capture_id in captures, f"measurement references unknown capture: {capture_id}")

        motion = measurement.get("motionEvidence")
        if not motion:
            continue
        input_times = [sample["timeMillis"] for sample in motion["inputTimeline"]]
        visual_times = [sample["timeMillis"] for sample in motion["visualSamples"]]
        require(input_times == sorted(input_times), f"input timeline is not ordered: {measurement['id']}")
        require(visual_times == sorted(visual_times), f"visual timeline is not ordered: {measurement['id']}")
        first_source_time = min(captures[capture_id]["frameTimestampMillis"] for capture_id in measurement["captureIds"])
        for sample in motion["visualSamples"]:
            capture_id = sample["captureId"]
            require(capture_id in measurement["captureIds"], f"visual sample is outside measurement captures: {capture_id}")
            source_delta = captures[capture_id]["frameTimestampMillis"] - first_source_time
            require(source_delta == sample["timeMillis"], f"visual time does not match source timestamp: {capture_id}")

    geometry = next(item["geometryEvidence"] for item in sets if item["scenarioId"] == "START_PRIMARY_GEOMETRY")
    require(geometry["smallTileBoundsPx"]["width"] == 99, "small tile width drifted")
    require(geometry["mediumTileBoundsPx"]["width"] == 210, "medium tile width drifted")
    require(geometry["wideTileBoundsPx"]["width"] == 432, "wide tile width drifted")
    require(geometry["seamPx"] == 12, "tile seam drifted")
    require(geometry["outerInsetsPx"]["left"] == 24, "left outer inset drifted")
    require(geometry["outerInsetsPx"]["right"] == 24, "right outer inset drifted")


def canonical_measurement_hash(measurements: dict) -> str:
    canonical = json.dumps(
        measurements["measurementSets"],
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode()
    return sha256(canonical).hexdigest()


def validate_review(measurements: dict, require_human_review: bool) -> None:
    review = measurements.get("review")
    if require_human_review:
        require(measurements["status"] == "HUMAN_REVIEWED", "status is not HUMAN_REVIEWED")
        require(review is not None, "human review is missing")
    if review is None:
        require(measurements["status"] == "MEASURED", "unreviewed evidence must remain MEASURED")
        return
    require(review["decision"] == "APPROVED", "human review decision is not APPROVED")
    require("codex" not in review["reviewedBy"].lower(), "Codex cannot approve its own measurements")
    require(review["reviewedMeasurementHash"] == canonical_measurement_hash(measurements), "reviewed measurement hash mismatch")


def validate_baseline_lock(lock: dict, source: dict, measurements: dict, require_human_review: bool) -> None:
    require(str(lock.get("stage")) == "2.5", "baseline lock stage is not 2.5")
    require(lock.get("approvedStage2Tag") == "launcher-engine-stage2-approved-v1", "approved Stage 2 tag drifted")
    require(
        lock.get("startingSha") == "5386da0575046f1f9a59742a4a0f5c78523fa5e6",
        "Stage 2.5 starting SHA drifted",
    )
    require(lock.get("nextStageStarted") is False, "Stage 3 was started before the reference gate")
    require(
        lock.get("visualSourceSha256") == source["recordings"][0]["sha256"],
        "baseline visual-source hash differs from the source manifest",
    )
    require(
        lock.get("canonicalMeasurementHash") == canonical_measurement_hash(measurements),
        "baseline canonical measurement hash drifted",
    )
    allowed_status = {"PENDING_HUMAN_REVIEW", "APPROVED"}
    require(lock.get("approvalStatus") in allowed_status, "unknown baseline approval status")
    if require_human_review:
        require(lock["approvalStatus"] == "APPROVED", "baseline lock is not APPROVED")
    if measurements.get("review") is not None:
        require(lock["approvalStatus"] == "APPROVED", "reviewed measurements require an approved baseline lock")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--require-human-review", action="store_true")
    args = parser.parse_args()
    try:
        schema = load_json(SCHEMA)
        measurements = load_json(MEASUREMENTS)
        Draft202012Validator.check_schema(schema)
        errors = sorted(
            Draft202012Validator(schema, format_checker=FormatChecker()).iter_errors(measurements),
            key=lambda error: list(error.path),
        )
        require(not errors, "schema validation failed: " + "; ".join(error.message for error in errors))
        source = load_json(SOURCES)
        validate_source_manifest(source, measurements)
        validate_capture_files(measurements)
        validate_measurement_semantics(measurements)
        validate_review(measurements, args.require_human_review)
        lock = load_json(BASELINE_LOCK)
        validate_baseline_lock(lock, source, measurements, args.require_human_review)
    except (EvidenceError, json.JSONDecodeError) as error:
        print(f"WP8_REFERENCE_VALIDATION=FAIL\n{error}", file=sys.stderr)
        return 1

    print(
        "WP8_REFERENCE_VALIDATION=PASS "
        f"status={measurements['status']} captures={len(measurements['captures'])} "
        f"measurementSets={len(measurements['measurementSets'])} "
        f"canonicalMeasurementHash={canonical_measurement_hash(measurements)}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
