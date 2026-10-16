#!/usr/bin/env python3
"""Analyze MEDIA-PERF-02-TUNE-01 shadow-owner telemetry from PID-filtered logcat."""

from __future__ import annotations

import argparse
import csv
import re
import statistics
import sys
from collections import Counter
from pathlib import Path
from typing import Iterable

EVENT_RE = re.compile(
    r"t_ms=(?P<t_ms>\d+)\s+"
    r"event=(?P<event>\S+)\s+"
    r"transition=(?P<transition>\d+)\s+"
    r"media=(?P<media>\S+)(?P<details>.*)$"
)

PROFILES = ("actual", "current", "neutral", "small", "ramped")
REQUIRED_EDGES = (
    ("video-lab-d", "video-lab-e"),
    ("video-lab-f", "video-lab-g"),
    ("video-lab-g", "video-lab-h"),
    ("video-lab-h", "video-lab-g"),
    ("video-lab-g", "video-lab-f"),
)

SAMPLE_COLUMNS = (
    "sample_id",
    "sample_t_ms",
    "kind",
    "motion",
    "reported_direction",
    "sampled_direction",
    "at_start",
    "at_end",
    "viewport_height_px",
    "first_index",
    "first_offset_px",
    "delta_px",
    "velocity_px_s",
    "smoothed_velocity_px_s",
    "drag_distance_px",
    "reversal_distance_px",
    "reversal_distance_fraction",
    "reversal_time_ms",
    "actual_owner",
    "actual_decision",
    "owner_age_ms",
    "active_visible",
    "active_viewport_visible",
    "active_center",
    "active_top",
    "active_bottom",
    "active_height",
    "candidate_visible",
    "candidate_viewport_visible",
    "candidate_center",
    "candidate_top",
    "candidate_bottom",
    "candidate_height",
    "current_owner",
    "current_candidate",
    "current_anchor",
    "current_active_score",
    "current_candidate_score",
    "current_score_delta",
    "current_switch_allowed",
    "neutral_owner",
    "neutral_candidate",
    "neutral_anchor",
    "neutral_active_score",
    "neutral_candidate_score",
    "neutral_score_delta",
    "neutral_switch_allowed",
    "small_owner",
    "small_candidate",
    "small_anchor",
    "small_active_score",
    "small_candidate_score",
    "small_score_delta",
    "small_switch_allowed",
    "ramped_owner",
    "ramped_candidate",
    "ramped_anchor",
    "ramped_active_score",
    "ramped_candidate_score",
    "ramped_score_delta",
    "ramped_switch_allowed",
    "previous_owner",
    "next_owner",
    "reason",
    "media",
    "count",
    "elapsed_ms",
    "buffer_bytes",
    "buffer_ms",
    "elapsed_since_feed_ms",
    "bytes",
    "duration_ms",
    "data_type",
    "rank",
    "visible",
    "aspect_ratio",
    "buffered_duration_ms",
    "error",
    "decoder",
    "initialized_t_ms",
    "initialization_duration_ms",
    "mime",
    "width",
    "height",
    "frame_rate",
    "bitrate",
    "decoder_reuse_result",
    "decoder_discard_reasons",
    "records",
    "dropped_records",
    "flush_reason",
    "details",
)


def parse_details(raw: str) -> dict[str, str]:
    details: dict[str, str] = {}
    for token in raw.strip().split():
        if "=" not in token:
            continue
        key, value = token.split("=", 1)
        details[key] = value
    return details


def parse_log(
    path: Path,
) -> tuple[list[dict[str, str]], list[dict[str, str]], list[dict[str, str]]]:
    samples: list[dict[str, str]] = []
    commits: list[dict[str, str]] = []
    flushes: list[dict[str, str]] = []
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = EVENT_RE.search(line)
        if not match:
            continue
        event = match.group("event")
        details = parse_details(match.group("details"))
        if event == "tune_sample":
            row = {column: "" for column in SAMPLE_COLUMNS}
            row.update({key: value for key, value in details.items() if key in row})
            row["media"] = details.get("media", match.group("media"))
            row["details"] = match.group("details").strip()
            samples.append(row)
        elif event == "tune_flush_begin":
            flushes.append(
                {
                    "records": details.get("records", "0"),
                    "dropped_records": details.get("dropped_records", "0"),
                    "flush_reason": details.get("reason", ""),
                }
            )
        elif event == "candidate_committed":
            commits.append(
                {
                    "t_ms": match.group("t_ms"),
                    "transition": match.group("transition"),
                    "media": match.group("media"),
                    "previous": details.get("previous", "none"),
                    "reason": details.get("reason", ""),
                    "motion": details.get("viewport_motion", ""),
                    "direction": details.get("scroll_direction", ""),
                    "edge": details.get("viewport_edge", ""),
                }
            )
    samples.sort(key=lambda row: (int(row.get("sample_t_ms") or 0), int(row.get("sample_id") or 0)))
    return samples, commits, flushes


def write_samples_csv(samples: list[dict[str, str]], output: Path | None) -> None:
    handle = output.open("w", encoding="utf-8", newline="") if output else sys.stdout
    try:
        writer = csv.DictWriter(handle, fieldnames=SAMPLE_COLUMNS)
        writer.writeheader()
        writer.writerows(samples)
    finally:
        if output:
            handle.close()


def profile_owner(row: dict[str, str], profile: str) -> str:
    if profile == "actual":
        return row.get("actual_decision") or row.get("actual_owner") or "none"
    return row.get(f"{profile}_owner") or "none"


def dedup_owner_sequence(
    samples: Iterable[dict[str, str]],
    profile: str,
) -> list[tuple[str, dict[str, str]]]:
    sequence: list[tuple[str, dict[str, str]]] = []
    previous = "none"
    for row in samples:
        if row.get("kind") != "decision":
            continue
        owner = profile_owner(row, profile)
        if owner in {"", "none"} or owner == previous:
            continue
        sequence.append((owner, row))
        previous = owner
    return sequence


def edge_counts(sequence: list[tuple[str, dict[str, str]]]) -> Counter[tuple[str, str]]:
    owners = [owner for owner, _ in sequence]
    return Counter(zip(owners, owners[1:]))


def numeric_values(samples: Iterable[dict[str, str]], key: str) -> list[float]:
    values: list[float] = []
    for row in samples:
        raw = row.get(key, "")
        if raw in {"", "none"}:
            continue
        try:
            values.append(float(raw))
        except ValueError:
            continue
    return values


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = round((len(ordered) - 1) * fraction)
    return ordered[index]


def profile_summary(samples: list[dict[str, str]], profile: str) -> dict[str, object]:
    sequence = dedup_owner_sequence(samples, profile)
    counts = edge_counts(sequence)
    changes = sequence[1:]
    drag_changes = [
        row for _, row in changes if row.get("motion") == "dragging"
    ]
    early_25 = sum(int(float(row.get("reversal_time_ms") or 999999) <= 25) for row in drag_changes)
    early_100 = sum(int(float(row.get("reversal_time_ms") or 999999) <= 100) for row in drag_changes)
    early_200 = sum(int(float(row.get("reversal_time_ms") or 999999) <= 200) for row in drag_changes)
    h_before_end = sum(
        1
        for owner, row in changes
        if owner == "video-lab-h" and row.get("at_end") != "true"
    )
    score_key = "ramped_score_delta" if profile == "actual" else f"{profile}_score_delta"
    score_values = numeric_values(drag_changes, score_key)
    return {
        "sequence": "->".join(owner for owner, _ in sequence) or "NONE",
        "owner_changes": max(0, len(sequence) - 1),
        "drag_owner_changes": len(drag_changes),
        "early_25_ms": early_25,
        "early_100_ms": early_100,
        "early_200_ms": early_200,
        "h_before_physical_end": h_before_end,
        "f_to_h": counts[("video-lab-f", "video-lab-h")],
        "g_h_ping_pong": max(0, counts[("video-lab-g", "video-lab-h")] - 1)
        + max(0, counts[("video-lab-h", "video-lab-g")] - 1),
        "required_edges": {
            f"{first}->{second}": counts[(first, second)] for first, second in REQUIRED_EDGES
        },
        "score_delta_median": statistics.median(score_values) if score_values else None,
        "score_delta_p10": percentile(score_values, 0.10),
        "score_delta_p90": percentile(score_values, 0.90),
    }


def build_summary(
    samples: list[dict[str, str]],
    commits: list[dict[str, str]],
    flushes: list[dict[str, str]] | None = None,
) -> dict[str, object]:
    decision_samples = [row for row in samples if row.get("kind") == "decision"]
    viewport_samples = [row for row in samples if row.get("kind") == "viewport"]
    playback_signals = [row for row in samples if row.get("kind") not in {"decision", "viewport", "owner_transition"}]
    profiles = {profile: profile_summary(samples, profile) for profile in PROFILES}
    flushes = flushes or []
    disagreements: dict[str, int] = {}
    for profile in PROFILES[1:]:
        disagreements[profile] = sum(
            1
            for row in decision_samples
            if profile_owner(row, profile) != profile_owner(row, "actual")
        )
    return {
        "tune_samples": len(samples),
        "decision_samples": len(decision_samples),
        "viewport_samples": len(viewport_samples),
        "playback_signals": len(playback_signals),
        "committed_transitions": len(commits),
        "flush_count": len(flushes),
        "tune_dropped_records": sum(int(row.get("dropped_records") or 0) for row in flushes),
        "flushed_records": sum(int(row.get("records") or 0) for row in flushes),
        "shadow_disagreements": disagreements,
        "profiles": profiles,
        "dropped_video_frames": sum(
            int(row.get("count") or 0)
            for row in playback_signals
            if row.get("kind") == "dropped_video_frames"
        ),
        "audio_underruns": sum(
            1 for row in playback_signals if row.get("kind") == "audio_underrun"
        ),
        "preload_completed": sum(
            1 for row in playback_signals if row.get("kind") == "preload_completed"
        ),
        "preload_errors": sum(
            1 for row in playback_signals if row.get("kind") == "preload_error"
        ),
        "video_decoder_initialized": sum(
            1 for row in playback_signals if row.get("kind") == "video_decoder_initialized"
        ),
        "video_decoder_released": sum(
            1 for row in playback_signals if row.get("kind") == "video_decoder_released"
        ),
        "video_format_changes": sum(
            1 for row in playback_signals if row.get("kind") == "video_format_changed"
        ),
        "selected_video_formats": sorted(
            {
                "/".join(
                    [
                        row.get("mime") or "unknown",
                        f"{row.get('width') or '?'}x{row.get('height') or '?'}",
                        f"{row.get('frame_rate') or '?'}fps",
                    ]
                )
                for row in playback_signals
                if row.get("kind") == "video_format_changed"
            }
        ),
        "surface_mounts": sum(
            1 for row in playback_signals if row.get("kind") == "surface_mounted"
        ),
        "surface_unmounts": sum(
            1 for row in playback_signals if row.get("kind") == "surface_unmounted"
        ),
        "poster_visibility_events": sum(
            1 for row in playback_signals if row.get("kind") == "poster_visibility"
        ),
        "first_frame_signals": sum(
            1 for row in playback_signals if row.get("kind") == "first_frame"
        ),
    }


def summary_lines(summary: dict[str, object]) -> list[str]:
    lines = [
        f"TUNE_SAMPLES={summary['tune_samples']}",
        f"DECISION_SAMPLES={summary['decision_samples']}",
        f"VIEWPORT_SAMPLES={summary['viewport_samples']}",
        f"PLAYBACK_SIGNALS={summary['playback_signals']}",
        f"COMMITTED_TRANSITIONS={summary['committed_transitions']}",
        f"FLUSH_COUNT={summary['flush_count']}",
        f"FLUSHED_RECORDS={summary['flushed_records']}",
        f"TUNE_DROPPED_RECORDS={summary['tune_dropped_records']}",
        f"DROPPED_VIDEO_FRAMES={summary['dropped_video_frames']}",
        f"AUDIO_UNDERRUNS={summary['audio_underruns']}",
        f"PRELOAD_COMPLETED={summary['preload_completed']}",
        f"PRELOAD_ERRORS={summary['preload_errors']}",
        f"VIDEO_DECODER_INITIALIZED={summary['video_decoder_initialized']}",
        f"VIDEO_DECODER_RELEASED={summary['video_decoder_released']}",
        f"VIDEO_FORMAT_CHANGES={summary['video_format_changes']}",
        f"SELECTED_VIDEO_FORMATS={','.join(summary['selected_video_formats']) or 'NONE'}",
        f"SURFACE_MOUNTS={summary['surface_mounts']}",
        f"SURFACE_UNMOUNTS={summary['surface_unmounts']}",
        f"POSTER_VISIBILITY_EVENTS={summary['poster_visibility_events']}",
        f"FIRST_FRAME_SIGNALS={summary['first_frame_signals']}",
    ]
    disagreements = summary["shadow_disagreements"]
    assert isinstance(disagreements, dict)
    profiles = summary["profiles"]
    assert isinstance(profiles, dict)
    for profile in PROFILES:
        profile_data = profiles[profile]
        assert isinstance(profile_data, dict)
        prefix = profile.upper()
        lines.extend(
            [
                f"{prefix}_SEQUENCE={profile_data['sequence']}",
                f"{prefix}_OWNER_CHANGES={profile_data['owner_changes']}",
                f"{prefix}_DRAG_OWNER_CHANGES={profile_data['drag_owner_changes']}",
                f"{prefix}_EARLY_25_MS={profile_data['early_25_ms']}",
                f"{prefix}_EARLY_100_MS={profile_data['early_100_ms']}",
                f"{prefix}_EARLY_200_MS={profile_data['early_200_ms']}",
                f"{prefix}_H_BEFORE_PHYSICAL_END={profile_data['h_before_physical_end']}",
                f"{prefix}_F_TO_H={profile_data['f_to_h']}",
                f"{prefix}_G_H_PING_PONG={profile_data['g_h_ping_pong']}",
            ]
        )
        required_edges = profile_data["required_edges"]
        assert isinstance(required_edges, dict)
        for edge, count in required_edges.items():
            lines.append(f"{prefix}_EDGE_{edge.replace('video-lab-', '').replace('->', '_TO_').upper()}={count}")
        for name in ("score_delta_median", "score_delta_p10", "score_delta_p90"):
            value = profile_data[name]
            lines.append(f"{prefix}_{name.upper()}={'NONE' if value is None else f'{value:.4f}'}")
        if profile != "actual":
            lines.append(f"{prefix}_DISAGREEMENTS_WITH_ACTUAL={disagreements[profile]}")
    return lines


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("logcat", type=Path)
    parser.add_argument("--samples-csv", type=Path)
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args()

    if not args.logcat.is_file():
        parser.error(f"logcat file not found: {args.logcat}")

    samples, commits, flushes = parse_log(args.logcat)
    write_samples_csv(samples, args.samples_csv)
    lines = summary_lines(build_summary(samples, commits, flushes))
    output = "\n".join(lines) + "\n"
    if args.summary:
        args.summary.write_text(output, encoding="utf-8")
    else:
        sys.stderr.write(output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
