#!/usr/bin/env python3
"""Guard the two integration paths: design package and raw Skija/Yoga."""
from __future__ import annotations

import hashlib
import pathlib
import re
import unittest

ROOT = pathlib.Path(__file__).resolve().parent.parent

REMOVED_LAYERS = (
    "component",
    "overlay",
    "shell",
    "scroll",
    "interaction",
    "view",
    "style",
    "input",
)
REMOVED_IMPORT = re.compile(
    r"^\s*import\s+(?:static\s+)?com\.atom\.lib\."
    r"(?:component|overlay|shell|scroll|interaction|view|style|input)\.",
    re.MULTILINE,
)
DOCS = (
    "getting-started.md",
    "api-contracts.md",
    "ai-guide.md",
    "STATUS.md",
    "packaging.md",
    "design-package.md",
)


def import_violations(source: str, relative: str) -> list[str]:
    if REMOVED_IMPORT.search(source):
        return [f"{relative}: imports a removed component/overlay/shell layer"]
    return []


def page_violations(source: str, relative: str) -> list[str]:
    out = []
    if re.search(r"D:/AtomChat-Main|D:\\\\AtomChat-Main|[A-Z]:/Users/", source):
        out.append(f"{relative}: development-machine path")
    if re.search(r"0x[0-9a-fA-F]{6,8}", source):
        out.append(f"{relative}: raw ARGB colour belongs in UiTokens")
    if re.search(r"\b(?:new\s+)?(?:Canvas|Paint|Surface|Rect)\b", source):
        out.append(f"{relative}: design-package page computes its own drawing geometry")
    return out


def violations(source: str, relative: str) -> list[str]:
    return page_violations(source, relative) + import_violations(source, relative)


def java_files(base: pathlib.Path) -> list[pathlib.Path]:
    return sorted(path for path in base.rglob("*.java") if path.is_file())


def validate() -> list[str]:
    errors = []
    example_root = ROOT / "examples"
    for path in java_files(example_root):
        relative = path.relative_to(ROOT).as_posix()
        errors += import_violations(path.read_text(encoding="utf-8"), relative)

    for host in ("a", "b"):
        files = java_files(example_root / host)
        if not files:
            errors.append(f"examples/{host}: no Java sources")
            continue
        sources = {path: path.read_text(encoding="utf-8") for path in files}
        combined = "\n".join(sources.values())
        if host == "a":
            for name in ("UiTokens", "SkiaDraw", "UiCards"):
                if name not in combined:
                    errors.append(f"examples/a: missing design-package usage {name}")
        else:
            if "YogaLayoutEngine" not in combined and not re.search(r"\bYoga\b", combined):
                errors.append("examples/b: missing Yoga layout usage")
            if not re.search(r"\bCanvas\b", combined) or not re.search(
                r"\.draw[A-Za-z]*\s*\(", combined
            ):
                errors.append("examples/b: missing native Canvas drawing call")

    for doc in DOCS:
        if not (ROOT / "docs" / doc).is_file():
            errors.append(f"docs/{doc}: missing")

    assets = ROOT / "platforms/shared/src/main/resources/assets"
    manifest = assets / "MANIFEST.sha256"
    if not manifest.is_file():
        errors.append("assets/MANIFEST.sha256: missing")
    else:
        listed = set()
        for line in manifest.read_text(encoding="ascii").splitlines():
            digest, rel = line.split("  ", 1)
            listed.add(rel)
            path = assets / rel
            if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                errors.append(f"asset manifest mismatch: {rel}")
        actual = {
            p.relative_to(assets).as_posix()
            for p in assets.rglob("*")
            if p.is_file() and p != manifest
        }
        if listed != actual:
            errors.append(
                f"asset manifest coverage differs: missing={sorted(actual - listed)}, "
                f"stale={sorted(listed - actual)}"
            )
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
        self.assertTrue(
            violations("new Rect(1,2,3,4) canvas.drawRect(0xff112233)", "fixture.java")
        )

    def test_injected_component_imports_fail(self):
        for layer in REMOVED_LAYERS:
            with self.subTest(layer=layer):
                source = f"import com.atom.lib.{layer}.Example;"
                self.assertTrue(
                    any("removed" in error for error in violations(source, "fixture.java")),
                    layer,
                )


if __name__ == "__main__":
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(GuardTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    raise SystemExit(0 if result.wasSuccessful() else 1)
