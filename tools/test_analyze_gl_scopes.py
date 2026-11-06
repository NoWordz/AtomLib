import unittest
from analyze_gl_scopes import analyze


def event(kind, thread=1, **values):
    return dict(type=kind, values=dict(eventThread=dict(javaThreadId=thread), **values))


def panel(frame, start, end, thread=1):
    return event("com.atomchat.PanelSegments", thread, frameId=frame,
                 startedAtNanos=start, panelNanos=f"PT0.{end-start:09d}S", surface="panel", success=True)


def capture(start, depth=0, thread=1):
    return event("com.atomlib.GlStateCapture", thread, startedAtNanos=start,
                 captureNanos="PT0.000000002S", depth=depth, success=True, textureUnits=192)


class ScopeAnalysisTest(unittest.TestCase):
    def test_counts_nested_scopes_and_uses_half_open_thread_local_intervals(self):
        events = [panel(1, 10, 20), panel(2, 20, 30), capture(10), capture(12, 1),
                  capture(20), capture(15, thread=2), capture(30), capture(0)]
        result = analyze(dict(recording=dict(events=list(reversed(events)))))
        self.assertEqual([2, 1], [row["captures"] for row in result["frames"]])
        self.assertEqual([1, 0], [row["nestedCaptures"] for row in result["frames"]])
        self.assertEqual([4, 2], [row["captureNanos"] for row in result["frames"]])
        self.assertEqual(3, result["unmatchedCaptures"])
        self.assertEqual([192], result["frames"][0]["textureUnits"])

    def test_partial_recording_boundaries_are_unknown_even_if_other_frames_match(self):
        result = analyze(dict(recording=dict(events=[panel(1, 10, 20), panel(2, 20, 30),
            panel(3, 30, 40), capture(25), capture(35), capture(45)])))
        self.assertEqual([None, None, 1], [row["captures"] for row in result["frames"]])
        self.assertEqual([0, 1, 1], [row["observedCaptures"] for row in result["frames"]])
        self.assertEqual(1, result["completeFrames"])

    def test_complete_zero_work_frame_does_not_require_another_nonzero_frame(self):
        result = analyze(dict(recording=dict(events=[capture(0), panel(1, 10, 20), capture(30)])))
        self.assertEqual(0, result["frames"][0]["captures"])
        self.assertEqual(1, result["completeFrames"])

    def test_rejects_missing_incomplete_and_ambiguous_data(self):
        for events in ([panel(1, 10, 20)], [capture(10)],
                       [panel(1, 10, 20), capture(30)],
                       [panel(1, 10, 20), panel(1, 20, 30), capture(10)],
                       [panel(1, 10, 20), panel(2, 15, 30), capture(10)],
                       [panel(1, 10, 20), capture(10), event("jdk.DataLoss")]):
            with self.subTest(events=events), self.assertRaises(ValueError):
                analyze(dict(recording=dict(events=events)))


if __name__ == "__main__":
    unittest.main()
