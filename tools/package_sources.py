#!/usr/bin/env python3
"""Create a deterministic source archive without build or VCS output."""
from __future__ import annotations

import argparse, hashlib, subprocess, zipfile
from pathlib import Path

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument("--output", type=Path, required=True)
out = parser.parse_args().output.resolve()
# Follow the repository's ignore rules, including before its initial commit.
# Walking directories directly also archived ignored logs and probe binaries.
listed = subprocess.check_output(
    ["git", "-C", str(root), "ls-files", "--cached", "--others", "--exclude-standard", "-z"]
)
files = sorted({root / name.decode("utf-8") for name in listed.split(b"\0") if name},
               key=lambda p: p.relative_to(root).as_posix())
files = [p for p in files if p.is_file() and p != out]
out.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in files:
        rel = path.relative_to(root).as_posix()
        info = zipfile.ZipInfo(rel, (2020, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        archive.writestr(info, path.read_bytes())
digest = hashlib.sha256(out.read_bytes()).hexdigest()
print(out)
print(digest)
