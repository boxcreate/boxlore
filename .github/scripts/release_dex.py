"""Inspect optimized DEX and R8 names using Android SDK tools."""

from __future__ import annotations

import re
import subprocess
import tempfile
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable

from release_classfile import Member

DEX_ENTRY = re.compile(r"(?:classes[0-9]*\.dex|[^/]+/dex/classes[0-9]*\.dex)")


@dataclass
class DexClass:
    name: str = ""
    super_name: str = ""
    fields: list[Member] = field(default_factory=list)
    methods: list[Member] = field(default_factory=list)
    signature: str = ""
    annotations: dict[str, list[str]] = field(default_factory=dict)
    parameter_annotations: dict[str, list[str]] = field(default_factory=dict)
    method_signatures: dict[str, str] = field(default_factory=dict)
    field_signatures: dict[str, str] = field(default_factory=dict)


def parse_dump(lines: Iterable[str]) -> dict[str, DexClass]:
    result = {}
    current = DexClass()
    class_index = None
    section = None
    annotation_method = None
    annotation_field = None
    annotation_parameters = False
    name = descriptor = None
    for line in lines:
        match = re.match(r"Class #([0-9]+)", line)
        if match and match[1] != class_index:
            current = DexClass()
            class_index = match[1]
            section = annotation_method = annotation_field = name = descriptor = None
            annotation_parameters = False
        match = re.search(r"Class descriptor\s*:\s*'L([^']+);'", line)
        if match:
            current.name = match[1]
            result[current.name] = current
        match = re.search(r"Superclass\s*:\s*'L([^']+);'", line)
        if match:
            current.super_name = match[1]
        match = re.match(r"Annotations on method #[0-9]+ '([^']+)'( parameters)?", line)
        if match:
            annotation_method = match[1]
            annotation_field = None
            annotation_parameters = bool(match[2])
        match = re.match(r"Annotations on field #[0-9]+ '([^']+)'", line)
        if match:
            annotation_field = match[1]
            annotation_method = None
            annotation_parameters = False
        if line.startswith("Annotations on class"):
            annotation_method = annotation_field = None
            annotation_parameters = False
        if re.match(r"\s+VISIBILITY_", line):
            if " Ldalvik/annotation/Signature;" in line:
                signature = "".join(re.findall(r'"([^"\\]*)"', line))
                if annotation_method:
                    current.method_signatures[annotation_method] = signature
                elif annotation_field:
                    current.field_signatures[annotation_field] = signature
                else:
                    current.signature = signature
            elif annotation_method:
                target = current.parameter_annotations if annotation_parameters else current.annotations
                target.setdefault(annotation_method, []).append(line.strip())
        if "static fields" in line.lower() or "instance fields" in line.lower():
            section = "fields"
        if "direct methods" in line.lower() or "virtual methods" in line.lower():
            section = "methods"
        if re.match(r"\s+#\d+\s+:", line):
            name = descriptor = None
        match = re.search(r"name\s*:\s*'([^']+)'", line)
        if match:
            name = match[1]
        match = re.search(r"type\s*:\s*'([^']+)'", line)
        if match:
            descriptor = match[1]
        match = re.search(r"access\s*:\s*0x([0-9a-fA-F]+)", line)
        if match and section and name and descriptor:
            getattr(current, section).append(Member(name, descriptor, int(match[1], 16)))
    return result


def inspect_archive(artifact: Path, dexdump: Path) -> tuple[dict[str, DexClass], int]:
    result = {}
    dex_bytes = 0
    with zipfile.ZipFile(artifact) as archive, tempfile.TemporaryDirectory(prefix="boxlore-release-") as temporary:
        entries = [name for name in archive.namelist() if DEX_ENTRY.fullmatch(name)]
        if not entries:
            raise ValueError(f"{artifact.name}: no DEX entries found")
        for index, entry in enumerate(entries):
            data = archive.read(entry)
            dex_bytes += len(data)
            dex = Path(temporary) / f"classes-{index}.dex"
            dex.write_bytes(data)
            # A file for stderr avoids pipe deadlock while streaming large stdout.
            with tempfile.TemporaryFile(mode="w+") as errors:
                with subprocess.Popen([str(dexdump), "-a", str(dex)], stdout=subprocess.PIPE,
                                      stderr=errors, text=True, encoding="utf-8", errors="replace") as process:
                    assert process.stdout is not None
                    result.update(parse_dump(process.stdout))
                    process.wait()
                    if process.returncode:
                        errors.seek(0)
                        raise ValueError(f"{artifact.name}: dexdump failed: {errors.read().strip()}")
    return result, dex_bytes


def read_mapping(path: Path) -> dict[str, str]:
    result = {}
    with path.open(encoding="utf-8") as source:
        for line in source:
            match = re.fullmatch(r"([^ #\s]+) -> ([^\s]+):\n?", line)
            if match:
                result[match[1].replace(".", "/")] = match[2].replace(".", "/")
    if not result:
        raise ValueError("R8 class mapping is empty")
    return result


def mapped_signature(signature: str, mapping: dict[str, str]) -> str:
    return re.sub(r"L([^;<]+)", lambda m: "L" + mapping.get(m[1], m[1]), signature)
