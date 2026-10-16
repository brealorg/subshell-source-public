#!/usr/bin/env python3
"""Summarize subshell MEDIA-PERF-01 timing events from PID-filtered logcat."""

from __future__ import annotations

import argparse
import csv
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

EVENT_RE = re.compile(
    r"t_ms=(?P<t_ms>\d+)\s+"
    r"event=(?P<event>\S+)\s+"
    r"transition=(?P<transition>\d+)\s+"
    r"media=(?P<media>\S+)(?P<details>.*)$"
)

COLUMNS = (
    "transition",
    "media",
    "previous_media",
    "reason",
    "scroll_direction",
    "viewport_motion",
    "viewport_edge",
    "preload_to_candidate_ms",
    "preload_to_first_frame_ms",
    "preloaded_source",
    "previous_release_to_candidate_ms",
    "previous_release_to_first_frame_ms",
    "candidate_to_surface_ms",
    "candidate_to_play_ms",
    "candidate_to_ready_ms",
    "candidate_to_first_frame_ms",
    "play_to_first_frame_ms",
    "prepare_to_ready_ms",
    "candidate_to_audible_ms",
)


def parse_details(raw: str) -> dict[str, str]:
    details: dict[str, str] = {}
    for token in raw.strip().split():
        if "=" not in token:
            continue
        key, value = token.split("=", 1)
        details[key] = value
    return details


def delta(events: dict[str, int], start: str, end: str) -> int | str:
    if start not in events or end not in events:
        return ""
    return events[end] - events[start]


def parse_log(path: Path) -> tuple[list[dict[str, object]], int]:
    transitions: dict[int, dict[str, object]] = defaultdict(
        lambda: {
            "media": "none",
            "previous_media": "none",
            "reason": "",
            "scroll_direction": "",
            "viewport_motion": "",
            "viewport_edge": "",
            "events": {},
            "details": {},
        }
    )
    preload_focus_by_media: dict[str, list[int]] = defaultdict(list)
    latest_release_time: int | None = None

    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = EVENT_RE.search(line)
        if not match:
            continue
        transition = int(match.group("transition"))
        event = match.group("event")
        timestamp = int(match.group("t_ms"))
        media = match.group("media")
        details = parse_details(match.group("details"))
        if event in {"preload_candidate_changed", "preload_focus_changed"} and media != "none":
            preload_focus_by_media[media].append(timestamp)
        if transition <= 0:
            continue
        record = transitions[transition]
        if event == "candidate_released":
            latest_release_time = timestamp
        if event == "candidate_committed":
            record["media"] = media
            record["previous_media"] = details.get("previous", "none")
            record["reason"] = details.get("reason", "")
            record["scroll_direction"] = details.get("scroll_direction", "")
            record["viewport_motion"] = details.get("viewport_motion", "")
            record["viewport_edge"] = details.get("viewport_edge", "")
            record["previous_release_time"] = latest_release_time
        if event == "media_item_set":
            record_details = record["details"]
            assert isinstance(record_details, dict)
            record_details.update(details)
        events = record["events"]
        assert isinstance(events, dict)
        events.setdefault(event, timestamp)

    rows: list[dict[str, object]] = []
    ignored_carryover_transitions = 0
    for transition in sorted(transitions):
        record = transitions[transition]
        events = record["events"]
        assert isinstance(events, dict)
        candidate_time = events.get("candidate_committed")
        if candidate_time is None:
            ignored_carryover_transitions += 1
            continue
        first_frame_time = events.get("first_frame_rendered")
        preload_times = [
            timestamp
            for timestamp in preload_focus_by_media.get(str(record["media"]), [])
            if candidate_time is not None and timestamp <= candidate_time
        ]
        preload_time = max(preload_times) if preload_times else None
        record_details = record["details"]
        assert isinstance(record_details, dict)
        previous_release_time = record.get("previous_release_time")
        assert previous_release_time is None or isinstance(previous_release_time, int)
        rows.append(
            {
                "transition": transition,
                "media": record["media"],
                "previous_media": record["previous_media"],
                "reason": record["reason"],
                "scroll_direction": record["scroll_direction"],
                "viewport_motion": record["viewport_motion"],
                "viewport_edge": record["viewport_edge"],
                "preload_to_candidate_ms": (
                    candidate_time - preload_time
                    if candidate_time is not None and preload_time is not None
                    else ""
                ),
                "preload_to_first_frame_ms": (
                    first_frame_time - preload_time
                    if first_frame_time is not None and preload_time is not None
                    else ""
                ),
                "preloaded_source": record_details.get("preloaded", ""),
                "previous_release_to_candidate_ms": (
                    candidate_time - previous_release_time
                    if previous_release_time is not None
                    else ""
                ),
                "previous_release_to_first_frame_ms": (
                    first_frame_time - previous_release_time
                    if first_frame_time is not None and previous_release_time is not None
                    else ""
                ),
                "candidate_to_surface_ms": delta(events, "candidate_committed", "surface_mounted"),
                "candidate_to_play_ms": delta(events, "candidate_committed", "play_requested"),
                "candidate_to_ready_ms": delta(events, "candidate_committed", "player_ready"),
                "candidate_to_first_frame_ms": delta(events, "candidate_committed", "first_frame_rendered"),
                "play_to_first_frame_ms": delta(events, "play_requested", "first_frame_rendered"),
                "prepare_to_ready_ms": delta(events, "prepare_called", "player_ready"),
                "candidate_to_audible_ms": delta(events, "candidate_committed", "audible_playback_started"),
            }
        )
    return rows, ignored_carryover_transitions


def write_csv(rows: list[dict[str, object]], output: Path | None) -> None:
    handle = output.open("w", encoding="utf-8", newline="") if output else sys.stdout
    try:
        writer = csv.DictWriter(handle, fieldnames=COLUMNS)
        writer.writeheader()
        writer.writerows(rows)
    finally:
        if output:
            handle.close()


def print_summary(rows: list[dict[str, object]], ignored_carryover_transitions: int) -> None:
    values = [
        int(row["candidate_to_first_frame_ms"])
        for row in rows
        if row["candidate_to_first_frame_ms"] != ""
    ]
    print(f"TRANSITIONS={len(rows)}", file=sys.stderr)
    print(f"IGNORED_CARRYOVER_TRANSITIONS={ignored_carryover_transitions}", file=sys.stderr)
    print(f"FIRST_FRAME_SAMPLES={len(values)}", file=sys.stderr)
    if values:
        print(f"FIRST_FRAME_MIN_MS={min(values)}", file=sys.stderr)
        print(f"FIRST_FRAME_MEDIAN_MS={statistics.median(values):g}", file=sys.stderr)
        print(f"FIRST_FRAME_MAX_MS={max(values)}", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("logcat", type=Path)
    parser.add_argument("--csv", type=Path)
    args = parser.parse_args()

    if not args.logcat.is_file():
        parser.error(f"logcat file not found: {args.logcat}")

    rows, ignored_carryover_transitions = parse_log(args.logcat)
    write_csv(rows, args.csv)
    print_summary(rows, ignored_carryover_transitions)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
