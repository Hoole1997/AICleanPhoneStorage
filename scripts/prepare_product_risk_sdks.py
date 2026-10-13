#!/usr/bin/env python3
"""Prepare audited SDK dependencies; never rewrite original vendor AARs.

Only XM's duplicate R8 annotation is removed. Broad consumer rules for Android,
AndroidX and Google are narrowed by the SDK source-set keep rules. Native
assets, manifests and vendor-specific consumer rules are preserved verbatim.
"""
from __future__ import annotations

import argparse
import hashlib
from io import BytesIO
from pathlib import Path
import re
from zipfile import ZipFile, ZIP_DEFLATED


ARTIFACTS = {
    "collect-1.3.2.aar": "f368cfdc16a9654733798ca9b2b23051e9526693aa06c96ca24fe5fb6fbc8c16",
    "xm-0.0.5.aar": "67f057f15130d3e5f538b786cd3a4388e4e45b2c122d6473b2463b5fcde1fd77",
}
MARKER = "com/android/tools/r8/annotations/LambdaMethod.class"
BROAD_RULE = re.compile(r"^-keep\s+(?:class|interface)\s+(?:android|androidx|com\.google)\.\*\*\s*\{\s*\*;\s*}\s*$")


def filtered_rules(raw: bytes) -> bytes:
    return ("\n".join(line for line in raw.decode("utf-8").splitlines()
                      if not BROAD_RULE.fullmatch(line.strip())) + "\n").encode("utf-8")


def remove_duplicate_marker(raw: bytes) -> bytes:
    output = BytesIO()
    with ZipFile(BytesIO(raw)) as source, ZipFile(output, "w", ZIP_DEFLATED) as target:
        for entry in source.infolist():
            if entry.filename != MARKER:
                target.writestr(entry, source.read(entry))
    return output.getvalue()


def audit(inputs: Path) -> None:
    all_classes = []
    for name, expected in ARTIFACTS.items():
        path = inputs / name
        if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
            raise ValueError(f"{name}: vendor artifact changed; audit before updating the pin")
        with ZipFile(path) as source, ZipFile(BytesIO(source.read("classes.jar"))) as classes:
            all_classes.append({name for name in classes.namelist() if name.endswith(".class")})
    if all_classes[0] & all_classes[1] != {MARKER}:
        raise ValueError("SDK duplicate class inventory changed; audit before preparing")


def prepare(inputs: Path, output: Path) -> None:
    audit(inputs)
    output.mkdir(parents=True, exist_ok=True)
    for name in ARTIFACTS:
        with ZipFile(inputs / name) as source, ZipFile(output / name, "w", ZIP_DEFLATED) as target:
            for entry in source.infolist():
                payload = source.read(entry)
                if entry.filename == "classes.jar" and name.startswith("xm-"):
                    payload = remove_duplicate_marker(payload)
                elif entry.filename == "proguard.txt":
                    payload = filtered_rules(payload)
                target.writestr(entry, payload)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path)
    parser.add_argument("--audit-only", action="store_true")
    args = parser.parse_args()
    if args.audit_only:
        audit(args.input_dir)
        print("SDK hashes and duplicate class inventory verified")
    else:
        if args.output_dir is None:
            parser.error("--output-dir is required unless --audit-only")
        prepare(args.input_dir, args.output_dir)
