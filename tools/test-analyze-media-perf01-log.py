#!/usr/bin/env python3
"""Regression test for subshell MEDIA-PERF-01 log analysis."""

from __future__ import annotations

import importlib.util
import tempfile
from pathlib import Path


def load_analyzer():
    analyzer_path = Path(__file__).with_name("analyze-media-perf01-log.py")
    spec = importlib.util.spec_from_file_location("subshell_media_perf_analyzer", analyzer_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Could not load analyzer: {analyzer_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main() -> int:
    analyzer = load_analyzer()
    log = """\
07-24 17:53:48.000 I subshell.media.perf: t_ms=1000 event=preload_candidate_changed transition=1 media=video-lab-d previous=none settled=false
07-24 17:53:48.010 I subshell.media.perf: t_ms=1010 event=candidate_released transition=1 media=video-lab-a reason=visibility_policy
07-24 17:53:48.020 I subshell.media.perf: t_ms=1020 event=preload_focus_changed transition=1 media=video-lab-b rank=1
07-24 17:53:48.100 I subshell.media.perf: t_ms=1100 event=candidate_committed transition=2 media=video-lab-b reason=viewport_drag_takeover previous=video-lab-a visible_fraction=0.440 scroll_direction=down viewport_motion=dragging viewport_edge=middle
07-24 17:53:48.120 I subshell.media.perf: t_ms=1120 event=surface_mounted transition=2 media=video-lab-b
07-24 17:53:48.130 I subshell.media.perf: t_ms=1130 event=media_item_set transition=2 media=video-lab-b source_index=0 preloaded=true
07-24 17:53:48.150 I subshell.media.perf: t_ms=1150 event=play_requested transition=2 media=video-lab-b same_media=false playback_state=ready
07-24 17:53:48.250 I subshell.media.perf: t_ms=1250 event=first_frame_rendered transition=2 media=video-lab-b
07-24 17:53:48.260 I subshell.media.perf: t_ms=1260 event=preload_candidate_changed transition=2 media=video-lab-c previous=video-lab-b settled=true
"""
    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / "logcat.txt"
        path.write_text(log, encoding="utf-8")
        rows, ignored = analyzer.parse_log(path)

    assert ignored == 1, ignored
    assert len(rows) == 1, rows
    row = rows[0]
    assert row["transition"] == 2, row
    assert row["media"] == "video-lab-b", row
    assert row["previous_media"] == "video-lab-a", row
    assert row["reason"] == "viewport_drag_takeover", row
    assert row["scroll_direction"] == "down", row
    assert row["viewport_motion"] == "dragging", row
    assert row["viewport_edge"] == "middle", row
    assert row["preloaded_source"] == "true", row
    assert row["preload_to_candidate_ms"] == 80, row
    assert row["candidate_to_first_frame_ms"] == 150, row
    assert row["previous_release_to_candidate_ms"] == 90, row
    assert row["previous_release_to_first_frame_ms"] == 240, row

    print("RESULT=SUBSHELL_MEDIA_PERF01_ANALYZER_REGRESSION_PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
