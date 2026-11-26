#!/usr/bin/env python3
"""Verify Flyway migration integrity (V1..V9 baseline hashes) and version sequence."""

import hashlib
import pathlib
import re
import sys

MIGRATION_NAME_RE = re.compile(r"^V([1-9]\d*)__(.+)\.sql$")


def load_baseline(baseline_path: pathlib.Path) -> dict[str, str]:
    if not baseline_path.is_file():
        raise RuntimeError(f"Baseline file missing: {baseline_path}")

    entries: dict[str, str] = {}
    for line_no, raw_line in enumerate(baseline_path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split()
        if len(parts) != 2 or not re.fullmatch(r"[0-9a-f]{64}", parts[0]):
            raise RuntimeError(f"Invalid baseline entry at {baseline_path}:{line_no}: {raw_line!r}")
        digest, filename = parts
        if filename in entries:
            raise RuntimeError(f"Duplicate baseline entry for {filename}")
        entries[filename] = digest

    if not entries:
        raise RuntimeError(f"Baseline file has no entries: {baseline_path}")
    return entries


def main() -> int:
    repo_root = pathlib.Path(__file__).resolve().parent.parent
    migration_dir = repo_root / "src" / "main" / "resources" / "db" / "migration"
    baseline_path = repo_root / "docs" / "architecture" / "flyway-baseline.sha256"

    if not migration_dir.is_dir():
        print(f"ERROR: migration directory not found: {migration_dir}", file=sys.stderr)
        return 1

    try:
        baseline = load_baseline(baseline_path)
    except RuntimeError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    sql_files = sorted(migration_dir.glob("*.sql"))
    if not sql_files:
        print(f"ERROR: no SQL migration files found in {migration_dir}", file=sys.stderr)
        return 1

    errors: list[str] = []
    versions: dict[int, list[str]] = {}
    actual_hashes: dict[str, str] = {}

    for path in sql_files:
        match = MIGRATION_NAME_RE.match(path.name)
        if not match:
            errors.append(f"Invalid Flyway migration filename (expected V<N>__<desc>.sql): {path.name}")
            continue
        version = int(match.group(1))
        versions.setdefault(version, []).append(path.name)
        actual_hashes[path.name] = hashlib.sha256(path.read_bytes()).hexdigest()

    for version, names in sorted(versions.items()):
        if len(names) > 1:
            errors.append(f"Duplicate migration version V{version}: {', '.join(names)}")

    if versions:
        sorted_versions = sorted(versions.keys())
        expected_versions = list(range(1, len(sorted_versions) + 1))
        if sorted_versions != expected_versions:
            errors.append(
                f"Migration versions must start at V1 and be consecutive without gaps; "
                f"found {sorted_versions}, expected {expected_versions}"
            )

    for filename, expected_hash in baseline.items():
        actual_hash = actual_hashes.get(filename)
        if actual_hash is None:
            errors.append(f"Baseline migration missing or renamed: {filename}")
        elif actual_hash != expected_hash:
            errors.append(
                f"Baseline migration modified (SHA-256 mismatch): {filename} "
                f"(expected {expected_hash}, got {actual_hash})"
            )

    if errors:
        for err in errors:
            print(f"ERROR: {err}", file=sys.stderr)
        return 1

    max_ver = max(versions.keys())
    print(
        f"OK: verified {len(baseline)} baseline migrations (V1..V{len(baseline)}) "
        f"and consecutive versions V1..V{max_ver}."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
