#!/usr/bin/env python3
"""Fail when active Flock-Sucker surfaces regress to the historical product identity."""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SELF = Path(__file__).resolve().relative_to(ROOT).as_posix()
HISTORICAL_PREFIXES = (
    "docs/superpowers/plans/",
    "docs/superpowers/specs/",
    "docs/hotcode/",
)
SKIP_PREFIXES = (
    "third_party/",
    "releases/",
)
LEGACY = re.compile(r"com\.flockyou|\bflockyou\b|flock[ -]you|\bFlockYou\b", re.IGNORECASE)


def tracked_files() -> list[str]:
    raw = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
    return [item.decode() for item in raw.split(b"\0") if item]


def historical(path: str) -> bool:
    return path.startswith(HISTORICAL_PREFIXES)


def skipped(path: str) -> bool:
    return path == SELF or path.startswith(SKIP_PREFIXES) or historical(path)


def allowed_content(path: str, line: str) -> bool:
    # Explicit upstream attribution is intentionally centralized here.
    if path in {"README.md", "NOTICE.md"} and "MaxwellDPS/Flock-You-Android" in line:
        return True
    # This regression test must name the forbidden package to assert its absence.
    if path.endswith("/ProductBrandingRegressionTest.kt") and "assertFalse" in line and "com.flockyou" in line:
        return True
    return False


def main() -> int:
    violations: list[str] = []
    for path in tracked_files():
        if skipped(path):
            continue
        if LEGACY.search(path):
            violations.append(f"PATH {path}")
        file_path = ROOT / path
        try:
            text = file_path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        for number, line in enumerate(text.splitlines(), 1):
            if LEGACY.search(line) and not allowed_content(path, line):
                violations.append(f"{path}:{number}: {line.strip()}")
    if violations:
        print("Canonical identity check FAILED. Unapproved historical identity remains:", file=sys.stderr)
        for violation in violations:
            print(violation, file=sys.stderr)
        return 1
    print("Canonical identity check PASS: active surfaces use Flock-Sucker / Inversion Labs identity.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
