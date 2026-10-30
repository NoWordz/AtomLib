#!/usr/bin/env python3
"""Build a deterministic AtomLib test-Maven archive for AC CI."""
from __future__ import annotations

import hashlib, zipfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
version = next(line.split("=", 1)[1].strip() for line in (root / "gradle.properties").read_text().splitlines()
               if line.startswith("atomlib_version="))
source = root / "build/test-maven"
output = root / "build" / f"atomlib-input-{version}.zip"
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in sorted(p for p in source.rglob("*") if p.is_file()):
        info = zipfile.ZipInfo(path.relative_to(source).as_posix(), (2020, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        archive.writestr(info, path.read_bytes())
digest = hashlib.sha256(output.read_bytes()).hexdigest()
(output.with_suffix(output.suffix + ".sha256")).write_text(f"{digest}  {output.name}\n", encoding="ascii")
print(output)
print(digest)
