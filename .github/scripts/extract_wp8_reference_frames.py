#!/usr/bin/env python3
"""Extract exact, full video frames for the Stage 2.5 evidence package.

This is an acquisition tool, not a CI dependency. Install the pinned packages in
`.github/requirements/stage25-extraction.txt`, then pass capture specifications
as `<capture-id>=<timestamp-millis>`. The source is decoded once in timestamp
order. Frames are emitted as lossless PNG without crop, resize, annotation, or
colour conversion beyond the decoder's RGB output.
"""

from __future__ import annotations

import argparse
from fractions import Fraction
import json
from pathlib import Path
import re

import av


CAPTURE_ID = re.compile(r"^[a-z0-9][a-z0-9._-]*$")


def parse_capture(value: str) -> tuple[str, int]:
    capture_id, separator, timestamp = value.partition("=")
    if not separator or not CAPTURE_ID.fullmatch(capture_id):
        raise argparse.ArgumentTypeError("capture must be <lowercase-id>=<timestamp-millis>")
    try:
        timestamp_millis = int(timestamp)
    except ValueError as error:
        raise argparse.ArgumentTypeError("timestamp must be an integer number of milliseconds") from error
    if timestamp_millis < 0:
        raise argparse.ArgumentTypeError("timestamp must be non-negative")
    return capture_id, timestamp_millis


def fraction_text(value: Fraction | None) -> str | None:
    return None if value is None else f"{value.numerator}/{value.denominator}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("captures", nargs="+", type=parse_capture)
    args = parser.parse_args()

    requested = sorted(args.captures, key=lambda item: item[1])
    if len({capture_id for capture_id, _ in requested}) != len(requested):
        parser.error("capture IDs must be unique")
    args.output.mkdir(parents=True, exist_ok=True)

    container = av.open(str(args.source))
    stream = container.streams.video[0]
    duration_millis = round(float(container.duration or 0) / 1_000)
    if requested[-1][1] > duration_millis:
        parser.error("requested timestamp is beyond the recording duration")

    next_index = 0
    extracted: list[dict[str, object]] = []
    for frame in container.decode(stream):
        if frame.time is None:
            continue
        frame_millis = round(float(frame.time) * 1_000)
        capture_id, requested_millis = requested[next_index]
        if frame_millis < requested_millis:
            continue

        output = args.output / f"{capture_id}.png"
        frame.to_image().save(output, format="PNG", optimize=False)
        extracted.append(
            {
                "captureId": capture_id,
                "requestedTimestampMillis": requested_millis,
                "frameTimestampMillis": frame_millis,
                "framePts": frame.pts,
                "path": output.as_posix(),
            }
        )
        next_index += 1
        if next_index == len(requested):
            break

    if next_index != len(requested):
        parser.error(f"decoded only {next_index} of {len(requested)} requested frames")

    result = {
        "source": args.source.as_posix(),
        "durationMillis": duration_millis,
        "dimensions": {"widthPx": stream.width, "heightPx": stream.height},
        "nominalFrameRate": fraction_text(stream.average_rate),
        "timeBase": fraction_text(stream.time_base),
        "codec": stream.codec_context.name,
        "pixelFormat": stream.codec_context.pix_fmt,
        "extractions": extracted,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
