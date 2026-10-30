#!/usr/bin/env python3
"""Guard the three integration paths and semantic page composition."""
from __future__ import annotations

import pathlib, re, unittest
import hashlib

ROOT = pathlib.Path(__file__).resolve().parent.parent


def violations(source: str, relative: str) -> list[str]:
    out = []
    if re.search(r"D:/AtomChat-Main|D:\\\\AtomChat-Main|[A-Z]:/Users/", source):
        out.append(f"{relative}: development-machine path")
    if re.search(r"0x[0-9a-fA-F]{6,8}", source):
        out.append(f"{relative}: raw ARGB colour belongs in ThemeSnapshot")
    if re.search(r"\b(?:new\s+)?(?:Canvas|Paint|Surface|Rect)\b", source):
        out.append(f"{relative}: normal page computes its own drawing geometry")
    return out


def validate() -> list[str]:
    errors = []
    for host in ("a", "b"):
        base = ROOT / "examples" / host
        text = (base / "src/main/java/com/atom/example" / host / "ExamplePages.java").read_text(encoding="utf-8")
        for name in ("Section", "SettingRow", "Slider", "AppShell", "ShellHeader", "TabBar"):
            if name not in text:
                errors.append(f"examples/{host}: missing integration path {name}")
        errors += violations(text, f"examples/{host}/ExamplePages.java")
        raw = (base / "src/main/java/com/atom/example" / host / "ExamplePainter.java").read_text(encoding="utf-8")
        if "drawString(" not in raw or "Yoga" not in raw:
            errors.append(f"examples/{host}: raw Skija/Yoga path missing")
    for doc in ("getting-started.md", "api-contracts.md", "ai-guide.md"):
        if not (ROOT / "docs" / doc).is_file():
            errors.append(f"docs/{doc}: missing")
    assets = ROOT / "platforms/shared/src/main/resources/assets"
    manifest = assets / "MANIFEST.sha256"
    if not manifest.is_file():
        errors.append("assets/MANIFEST.sha256: missing")
    else:
        listed = set()
        for line in manifest.read_text(encoding="ascii").splitlines():
            digest, rel = line.split("  ", 1); listed.add(rel)
            path = assets / rel
            if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                errors.append(f"asset manifest mismatch: {rel}")
        actual = {p.relative_to(assets).as_posix() for p in assets.rglob("*") if p.is_file() and p != manifest}
        if listed != actual:
            errors.append(f"asset manifest coverage differs: missing={sorted(actual-listed)}, stale={sorted(listed-actual)}")
        for path in assets.rglob("*"):
            if path.is_file():
                head = path.read_bytes()[:4]
                if head in (b"OTTO", b"ttcf", b"wOFF", b"wOF2") or head == b"\x00\x01\x00\x00":
                    errors.append(f"font bytes shipped: {path.relative_to(ROOT)}")
    return errors


class GuardTests(unittest.TestCase):
    def test_repository_examples(self):
        self.assertEqual([], validate())
    def test_injected_raw_geometry_and_colour_fail(self):
        self.assertTrue(violations("new Rect(1,2,3,4) canvas.drawRect(0xff112233)", "fixture.java"))
    def test_semantic_composition_passes(self):
        self.assertEqual([], violations("new Section(\"page\", Section.Kind.SCROLL, children)", "fixture.java"))


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(GuardTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)
