#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile

MODULE_PATH = Path(__file__).with_name("analyze-media-perf02-tune-log.py")
spec = importlib.util.spec_from_file_location("tune_analyzer", MODULE_PATH)
assert spec and spec.loader
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def sample_line(sample_id: int, t_ms: int, actual: str, neutral: str, reversal_ms: int) -> str:
    return (
        "07-24 00:00:00.000 1 1 I subshell.media.perf: "
        f"t_ms=9999 event=tune_sample transition=9 media=video-lab-h "
        f"sample_id={sample_id} sample_t_ms={t_ms} kind=decision "
        "motion=dragging reported_direction=down sampled_direction=down "
        "at_start=false at_end=false viewport_height_px=1000 first_index=4 "
        "first_offset_px=0 delta_px=20 velocity_px_s=400 smoothed_velocity_px_s=350 "
        "drag_distance_px=120 reversal_distance_px=40 reversal_distance_fraction=0.04 "
        f"reversal_time_ms={reversal_ms} actual_owner=video-lab-f actual_decision={actual} "
        "owner_age_ms=100 active_visible=0.5 active_viewport_visible=0.2 active_center=-0.1 "
        "active_top=0.1 active_bottom=0.6 active_height=0.5 "
        "candidate_visible=0.4 candidate_viewport_visible=0.1 candidate_center=0.05 "
        "candidate_top=0.4 candidate_bottom=0.5 candidate_height=0.1 "
        f"current_owner={actual} current_candidate=video-lab-g current_anchor=0.1 "
        "current_active_score=0.2 current_candidate_score=0.05 current_score_delta=0.15 "
        "current_switch_allowed=true "
        f"neutral_owner={neutral} neutral_candidate=video-lab-g neutral_anchor=0.0 "
        "neutral_active_score=0.1 neutral_candidate_score=0.05 neutral_score_delta=0.05 "
        "neutral_switch_allowed=false "
        "small_owner=video-lab-g small_candidate=video-lab-g small_anchor=0.04 "
        "small_active_score=0.14 small_candidate_score=0.01 small_score_delta=0.13 "
        "small_switch_allowed=true "
        "ramped_owner=video-lab-f ramped_candidate=video-lab-g ramped_anchor=0.01 "
        "ramped_active_score=0.11 ramped_candidate_score=0.04 ramped_score_delta=0.07 "
        "ramped_switch_allowed=false"
    )


with tempfile.TemporaryDirectory() as directory:
    path = Path(directory) / "logcat.txt"
    path.write_text(
        "\n".join(
            [
                sample_line(1, 1000, "video-lab-f", "video-lab-f", 0),
                sample_line(2, 1020, "video-lab-g", "video-lab-f", 20),
                sample_line(3, 1100, "video-lab-h", "video-lab-g", 100),
                "07-24 00:00:00.000 1 1 I subshell.media.perf: "
                "t_ms=9999 event=tune_sample transition=9 media=video-lab-h "
                "sample_id=4 sample_t_ms=1110 kind=dropped_video_frames media=video-lab-h count=2 elapsed_ms=100",
                "07-24 00:00:00.000 1 1 I subshell.media.perf: "
                "t_ms=9999 event=tune_sample transition=9 media=video-lab-h "
                "sample_id=5 sample_t_ms=1120 kind=video_format_changed media=video-lab-g "
                "mime=video/avc width=1280 height=720 frame_rate=60.0 bitrate=2500000",
                "07-24 00:00:00.000 1 1 I subshell.media.perf: "
                "t_ms=9999 event=tune_flush_begin transition=9 media=video-lab-h "
                "reason=background records=5 dropped_records=0",
                "07-24 00:00:00.000 1 1 I subshell.media.perf: "
                "t_ms=1000 event=candidate_committed transition=10 media=video-lab-g "
                "reason=viewport_drag_takeover previous=video-lab-f viewport_motion=dragging",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    samples, commits, flushes = module.parse_log(path)
    assert len(samples) == 5
    assert len(commits) == 1
    assert len(flushes) == 1
    summary = module.build_summary(samples, commits, flushes)
    assert summary["dropped_video_frames"] == 2
    assert summary["tune_dropped_records"] == 0
    assert summary["video_format_changes"] == 1
    assert summary["selected_video_formats"] == ["video/avc/1280x720/60.0fps"]
    actual = summary["profiles"]["actual"]
    neutral = summary["profiles"]["neutral"]
    assert actual["sequence"] == "video-lab-f->video-lab-g->video-lab-h"
    assert actual["early_25_ms"] == 1
    assert neutral["sequence"] == "video-lab-f->video-lab-g"
    assert summary["shadow_disagreements"]["current"] == 0
    assert summary["shadow_disagreements"]["neutral"] == 2

print("RESULT=SUBSHELL_MEDIA_PERF02_TUNE_ANALYZER_REGRESSION_PASS")
