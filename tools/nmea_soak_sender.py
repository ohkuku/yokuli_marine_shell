#!/usr/bin/env python3
"""Bounded four-port NMEA load/fault sender for the P6/P7 acceptance run."""

import argparse
import asyncio
import json
import time
from dataclasses import dataclass, asdict

DEFAULT_DURATION_SECONDS = 30 * 60
DEFAULT_CONNECTIONS = 4
DEFAULT_TOTAL_RATE = 100


@dataclass
class Stats:
    accepted_clients: int = 0
    active_clients: int = 0
    peak_clients: int = 0
    valid_frames: int = 0
    bad_frames: int = 0
    sent_bytes: int = 0
    write_failures: int = 0
    silent_windows: int = 0


def nmea_frame(sequence: int, bad: bool) -> bytes:
    body = f"GPRMC,120000.00,A,3650.0000,S,17445.0000,E,4.2,183.0,010126,,,A"
    checksum = 0
    for byte in body.encode("ascii"):
        checksum ^= byte
    suffix = "00" if bad else f"{checksum:02X}"
    return f"${body}*{suffix}\r\n".encode("ascii")


async def run_sender(args: argparse.Namespace) -> dict:
    stats = Stats()
    stop = asyncio.Event()
    writers: set[asyncio.StreamWriter] = set()
    handler_tasks: set[asyncio.Task] = set()
    started = time.monotonic()
    per_connection_rate = args.total_rate / args.connections

    async def handle(index: int, _reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
        task = asyncio.current_task()
        if task is not None:
            handler_tasks.add(task)
        writers.add(writer)
        stats.accepted_clients += 1
        stats.active_clients += 1
        stats.peak_clients = max(stats.peak_clients, stats.active_clients)
        sequence = 0
        in_silent_window = False
        try:
            while not stop.is_set():
                elapsed = time.monotonic() - started
                cycle = int(elapsed) % args.silent_every
                silent = index == 0 and elapsed >= args.silent_every and cycle < args.silent_for
                if silent:
                    if not in_silent_window:
                        stats.silent_windows += 1
                    in_silent_window = True
                    await asyncio.sleep(min(0.1, 1.0 / per_connection_rate))
                    continue
                in_silent_window = False
                sequence += 1
                bad = args.bad_frame_every > 0 and sequence % args.bad_frame_every == 0
                frame = nmea_frame(sequence, bad)
                writer.write(frame)
                await writer.drain()
                stats.bad_frames += int(bad)
                stats.valid_frames += int(not bad)
                stats.sent_bytes += len(frame)
                await asyncio.sleep(1.0 / per_connection_rate)
        except (ConnectionError, asyncio.CancelledError):
            if not stop.is_set():
                stats.write_failures += 1
        finally:
            stats.active_clients -= 1
            writers.discard(writer)
            writer.close()
            try:
                await writer.wait_closed()
            except ConnectionError:
                pass
            if task is not None:
                handler_tasks.discard(task)

    servers = []
    for index in range(args.connections):
        server = await asyncio.start_server(
            lambda reader, writer, i=index: handle(i, reader, writer),
            args.host,
            args.base_port + index,
        )
        servers.append(server)

    async def sink(port: int) -> None:
        reader, writer = await asyncio.open_connection(args.host, port)
        try:
            while not stop.is_set():
                await reader.read(4096)
        finally:
            writer.close()
            await writer.wait_closed()

    sinks = []
    if args.self_connect:
        sinks = [asyncio.create_task(sink(args.base_port + i)) for i in range(args.connections)]
    await asyncio.sleep(args.duration)
    stop.set()
    for server in servers:
        server.close()
    await asyncio.gather(*(server.wait_closed() for server in servers))
    for writer in list(writers):
        writer.close()
    if handler_tasks:
        await asyncio.gather(*list(handler_tasks), return_exceptions=True)
    for task in sinks:
        task.cancel()
    if sinks:
        await asyncio.gather(*sinks, return_exceptions=True)

    return {
        "configuration": {
            "duration_seconds": args.duration,
            "connections": args.connections,
            "total_rate": args.total_rate,
            "faults": ["bad-frame", "silent"],
        },
        "elapsed_seconds": round(time.monotonic() - started, 3),
        **asdict(stats),
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="P6 NMEA soak sender with bad-frame and silent fault injection")
    parser.add_argument("--duration", type=float, default=DEFAULT_DURATION_SECONDS)
    parser.add_argument("--connections", type=int, default=DEFAULT_CONNECTIONS)
    parser.add_argument("--total-rate", type=int, default=DEFAULT_TOTAL_RATE)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--base-port", type=int, default=12110)
    parser.add_argument("--bad-frame-every", type=int, default=97)
    parser.add_argument("--silent-every", type=int, default=300)
    parser.add_argument("--silent-for", type=int, default=12)
    parser.add_argument("--self-connect", action="store_true")
    args = parser.parse_args()
    if args.duration <= 0 or args.connections <= 0 or args.total_rate < args.connections:
        parser.error("duration, connections, and per-connection rate must be positive")
    if args.base_port < 1 or args.base_port + args.connections - 1 > 65535:
        parser.error("port range is invalid")
    if args.silent_every <= 0 or args.silent_for < 0 or args.silent_for >= args.silent_every:
        parser.error("silent window is invalid")
    return args


if __name__ == "__main__":
    print(json.dumps(asyncio.run(run_sender(parse_args())), sort_keys=True))
