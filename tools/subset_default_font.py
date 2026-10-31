#!/usr/bin/env python3
"""Rebuild AtomLib's bundled default fonts from official Noto Sans SC sources.

AtomLib ships a subsetted Noto Sans SC as its default font (direction F in
docs/next-directions.md). The charset contract lives in
tools/default-font-charset.txt; this tool is the auditable way to regenerate
the shipped OTFs from a full Noto source font when the contract changes or a
newer Noto release is adopted. The variable wght axis is kept as-is (no
instancing), layout features and name records are preserved, so metrics of
codepoints shared with a previously shipped file must not drift; the verify
mode checks exactly that.

Modes:
  subset   SOURCE_FONT -> subsetted OTF from the charset file
  verify   OLD_FONT NEW_FONT -> codepoint-by-codepoint drift report
           (presence, advance width + lsb, decomposed outline, fvar axes)
  coverage SOURCE_FONT -> which requested codepoints the source lacks

Requires fontTools (pyftsubset). Example:

  python tools/subset_default_font.py coverage \\
      --font NotoSansSC-VF.ttf --charset tools/default-font-charset.txt
  python tools/subset_default_font.py subset \\
      --font NotoSansSC-VF.ttf --charset tools/default-font-charset.txt \\
      --output runtime/src/main/resources/com/atom/lib/fonts/bundled-default.otf
  python tools/subset_default_font.py verify bundled.otf bundled-default.otf
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.subset import Options, Subsetter
from fontTools.ttLib import TTFont


def parse_charset(path: Path) -> list[int]:
    """One codepoint per line: hex (00A0), U+00A0 or 0x00A0; '#' comments."""
    cps = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        text = line.split("#", 1)[0].strip()
        if not text:
            continue
        if text[:2].lower() == "u+":
            text = text[2:]
        elif text[:2].lower() == "0x":
            text = text[2:]
        cps.add(int(text, 16))
    return sorted(cps)


def load_source(path: Path) -> TTFont:
    return TTFont(str(path))


def subset_font(source: Path, charset: Path, output: Path) -> None:
    codepoints = parse_charset(charset)
    font = load_source(source)
    cmap = set(font.getBestCmap().keys())
    requested = len(codepoints)
    covered = [cp for cp in codepoints if cp in cmap]

    options = Options()
    # Keep every layout feature and name record: the shipped files carry the
    # full CJK vert/palt/ruby feature set and the original name table.
    options.layout_features = ["*"]
    options.name_IDs = ["*"]
    options.name_languages = ["*"]
    options.name_legacy = True
    options.notdef_outline = True
    options.hinting = True
    options.glyph_names = False  # shipped files use post format 3.0
    # Variable axes (fvar/avar/gvar/HVAR/STAT) are subset per glyph but never
    # instanced; the wght axis and its default stay untouched.
    subsetter = Subsetter(options=options)
    subsetter.populate(unicodes=covered)
    subsetter.subset(font)

    output.parent.mkdir(parents=True, exist_ok=True)
    font.save(str(output))
    print(f"requested {requested} codepoints, source covers {len(covered)}, "
          f"wrote {output} ({output.stat().st_size} bytes, "
          f"{font['maxp'].numGlyphs} glyphs)")


def _outlines(font: TTFont) -> dict[int, tuple]:
    """Map codepoint -> decomposed outline recording at the default location."""
    glyphset = font.getGlyphSet()
    recording = {}
    for cp, gname in font.getBestCmap().items():
        pen = DecomposingRecordingPen(glyphset)
        try:
            glyphset[gname].draw(pen)
            recording[cp] = pen.value
        except Exception as error:  # noqa: BLE001 - reported as drift
            recording[cp] = ("error", repr(error))
    return recording


def _metrics(font: TTFont) -> dict[int, tuple[int, int]]:
    hmtx = font["hmtx"]
    return {cp: hmtx[gname] for cp, gname in font.getBestCmap().items()}


def verify_fonts(old_path: Path, new_path: Path) -> int:
    old = load_source(old_path)
    new = load_source(new_path)
    old_cmap = old.getBestCmap()
    new_cmap = new.getBestCmap()
    shared = sorted(set(old_cmap) & set(new_cmap))
    only_old = sorted(set(old_cmap) - set(new_cmap))
    only_new = sorted(set(new_cmap) - set(old_cmap))

    print(f"old: {old_path} ({old['maxp'].numGlyphs} glyphs, {len(old_cmap)} cmap entries)")
    print(f"new: {new_path} ({new['maxp'].numGlyphs} glyphs, {len(new_cmap)} cmap entries)")
    print(f"shared codepoints: {len(shared)}, removed: {len(only_old)}, added: {len(only_new)}")

    old_axes = [(a.axisTag, a.minValue, a.defaultValue, a.maxValue)
                for a in old["fvar"].axes] if "fvar" in old else None
    new_axes = [(a.axisTag, a.minValue, a.defaultValue, a.maxValue)
                for a in new["fvar"].axes] if "fvar" in new else None
    axes_match = old_axes == new_axes
    print(f"fvar axes old={old_axes} new={new_axes} match={axes_match}")
    upem_match = old["head"].unitsPerEm == new["head"].unitsPerEm
    print(f"unitsPerEm old={old['head'].unitsPerEm} new={new['head'].unitsPerEm} match={upem_match}")

    old_metrics, new_metrics = _metrics(old), _metrics(new)
    drift_metrics = [cp for cp in shared if old_metrics[cp] != new_metrics.get(cp)]

    old_outline = new_outline = None
    drift_outline: list[int] = []
    if drift_metrics:
        # Metrics already drifted; outline comparison would compare different
        # fonts anyway, but still run it for the report.
        pass
    old_outline = _outlines(old)
    new_outline = _outlines(new)
    drift_outline = [cp for cp in shared if old_outline[cp] != new_outline.get(cp)]

    def preview(cps: list[int]) -> str:
        return " ".join(f"{cp:04X}" for cp in cps[:12]) + ("..." if len(cps) > 12 else "")

    print(f"advance/lsb drift: {len(drift_metrics)}" +
          (f" [{preview(drift_metrics)}]" if drift_metrics else ""))
    print(f"outline drift: {len(drift_outline)}" +
          (f" [{preview(drift_outline)}]" if drift_outline else ""))
    zero = axes_match and upem_match and not drift_metrics and not drift_outline
    print("RESULT: " + ("zero drift on all shared codepoints" if zero else "DRIFT DETECTED"))
    return 0 if zero else 1


def coverage(source: Path, charset: Path) -> int:
    codepoints = parse_charset(charset)
    cmap = set(load_source(source).getBestCmap().keys())
    missing = [cp for cp in codepoints if cp not in cmap]
    print(f"charset {len(codepoints)} codepoints, source covers {len(codepoints) - len(missing)}")
    for cp in missing:
        print(f"{cp:04X}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    sub = commands.add_parser("subset", help="subset a source font to the charset contract")
    sub.add_argument("--font", required=True, type=Path)
    sub.add_argument("--charset", required=True, type=Path)
    sub.add_argument("--output", required=True, type=Path)

    ver = commands.add_parser("verify", help="drift report between two fonts")
    ver.add_argument("old", type=Path)
    ver.add_argument("new", type=Path)

    cov = commands.add_parser("coverage", help="requested codepoints missing from a source font")
    cov.add_argument("--font", required=True, type=Path)
    cov.add_argument("--charset", required=True, type=Path)

    args = parser.parse_args()
    if args.command == "subset":
        subset_font(args.font, args.charset, args.output)
        return 0
    if args.command == "verify":
        return verify_fonts(args.old, args.new)
    return coverage(args.font, args.charset)


if __name__ == "__main__":
    sys.exit(main())
