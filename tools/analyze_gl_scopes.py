"""Count GL captures inside recorded AtomChat panel intervals, on the same thread.

Input: jfr print --json --events com.atomlib.GlStateCapture,com.atomchat.PanelSegments,jdk.DataLoss file.jfr
"""
import argparse
from bisect import bisect_right
from collections import defaultdict
import json
from pathlib import Path
import re


def nanos(value):
    # jfr print serializes @Timespan fields as ISO-8601, not raw integers.
    if isinstance(value, int):
        return value
    match = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)(?:\.(\d{1,9}))?S)?", value)
    if not match or not any(match.groups()):
        raise ValueError(f"Unsupported JFR duration: {value}")
    hours, minutes, seconds, fraction = match.groups()
    return ((int(hours or 0)*60 + int(minutes or 0))*60 + int(seconds or 0))*1_000_000_000 + int((fraction or "").ljust(9, "0"))


def analyze(document):
    events = document["recording"]["events"]
    panels, captures = defaultdict(list), []
    seen = set()
    for event in events:
        kind, value = event["type"], event["values"]
        if kind == "jdk.DataLoss":
            raise ValueError("JFR reports data loss; counts are incomplete")
        if kind not in ("com.atomchat.PanelSegments", "com.atomlib.GlStateCapture"):
            continue
        thread = value["eventThread"]["javaThreadId"]
        if kind == "com.atomlib.GlStateCapture":
            captures.append((thread, value))
            continue
        key = (thread, value["frameId"])
        if key in seen:
            raise ValueError(f"Duplicate panel frame: {key}")
        seen.add(key)
        start, duration = value["startedAtNanos"], nanos(value["panelNanos"])
        if duration < 0:
            raise ValueError("Unsampled panel interval")
        panels[thread].append(dict(threadId=thread, frameId=value["frameId"],
            surface=value["surface"], success=value["success"], startedAtNanos=start,
            endedAtNanos=start + duration, captures=0, nestedCaptures=0,
            maxDepth=0, failedCaptures=0, captureNanos=0, textureUnits=[]))
    if not captures or not panels:
        raise ValueError("Need both GL capture and panel events; missing data is not zero work")
    starts = {}
    for thread, rows in panels.items():
        rows.sort(key=lambda row: row["startedAtNanos"])
        for previous, current in zip(rows, rows[1:]):
            if previous["endedAtNanos"] > current["startedAtNanos"]:
                raise ValueError("Overlapping panel intervals are ambiguous")
        starts[thread] = [row["startedAtNanos"] for row in rows]
    unmatched = 0
    for thread, capture in captures:
        index = bisect_right(starts.get(thread, []), capture["startedAtNanos"]) - 1
        if index < 0 or capture["startedAtNanos"] >= panels[thread][index]["endedAtNanos"]:
            unmatched += 1
            continue
        row = panels[thread][index]
        row["captures"] += 1
        row["nestedCaptures"] += int(capture["depth"] > 0)
        row["maxDepth"] = max(row["maxDepth"], capture["depth"])
        row["failedCaptures"] += int(not capture["success"])
        row["captureNanos"] += nanos(capture["captureNanos"])
        units = capture["textureUnits"]
        if units not in row["textureUnits"]:
            row["textureUnits"].append(units)
    rows = sorted((row for group in panels.values() for row in group),
                  key=lambda row: (row["startedAtNanos"], row["threadId"]))
    matched = sum(row["captures"] for row in rows)
    coverage = defaultdict(list)
    for thread, capture in captures:
        coverage[thread].append(capture["startedAtNanos"])
    bounds = {thread: (min(stamps), max(stamps)) for thread, stamps in coverage.items()}
    for row in rows:
        limits = bounds.get(row["threadId"])
        complete = bool(limits and limits[0] <= row["startedAtNanos"]
                        and row["endedAtNanos"] <= limits[1])
        row["coverage"] = "interior" if complete else "boundary_unknown"
        row["observedCaptures"] = row["captures"]
        if not complete:
            row["captures"] = None
    complete_frames = sum(row["captures"] is not None for row in rows)
    if not matched and not complete_frames:
        raise ValueError("No captures matched or covered panel intervals; check recording boundaries")
    return dict(scope="AtomChat panel intervals, not whole Minecraft frames",
                totalCaptures=len(captures), matchedCaptures=matched,
                unmatchedCaptures=unmatched,
                completeFrames=complete_frames, frames=rows)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("events", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = analyze(json.loads(args.events.read_text(encoding="utf-8-sig")))
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(result['frames'])} panels; {result['matchedCaptures']} matched captures; "
          f"{result['unmatchedCaptures']} outside panels")
