#!/usr/bin/env python3
"""Fail publication when optimized APK/AAB reflection or stored-JSON contracts break."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

from release_classfile import ClassFile, load_project_classes
from release_dex import DexClass, inspect_archive, mapped_signature, read_mapping

APP_PREFIX = "cx/aswin/boxlore/"
GSON_ROOTS = {
    APP_PREFIX + "core/model/Episode",
    APP_PREFIX + "core/model/Person",
    APP_PREFIX + "core/model/Transcript",
    APP_PREFIX + "core/catalog/backup/BoxLoreBackup",
    APP_PREFIX + "core/catalog/content/RecentSectionIntentRecord",
    APP_PREFIX + "core/network/model/TrendingFeed",
    APP_PREFIX + "core/network/model/ContentCatalogResponse",
}
WORK_BASES = {"androidx/work/ListenableWorker", "androidx/work/Worker", "androidx/work/CoroutineWorker"}
INPUT_MERGERS = {"androidx/work/OverwritingInputMerger", "androidx/work/ArrayCreatingInputMerger"}
API = APP_PREFIX + "core/network/BoxLoreApi"
HTTP_METHODS = ("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS", "HTTP")
DEBUG_ONLY = {"com/google/firebase/appcheck/debug/DebugAppCheckProviderFactory",
              "com/google/firebase/appcheck/debug/FirebaseAppCheckDebugRegistrar",
              APP_PREFIX + "debug/TestNotificationReceiver"}
WIDGET_MODELS = {APP_PREFIX + "feature/widgets/" + name for name in
                 ("NowPlayingWidgetSnapshot", "LibraryWidgetSnapshot", "WidgetShowRow", "WidgetEpisodeRow")}


def referenced_classes(signature: str) -> set[str]:
    return set(re.findall(r"L([^;<]+)", signature))


def json_graph(roots: set[str], classes: dict[str, ClassFile]) -> set[str]:
    pending, visited = list(roots), set()
    while pending:
        name = pending.pop()
        if name in visited or not name.startswith(APP_PREFIX):
            continue
        if name not in classes:
            raise ValueError(f"JSON contract class missing before R8: {name}")
        visited.add(name)
        item = classes[name]
        if item.access & 0x4000:
            continue
        for member in item.fields:
            if not member.access & (0x0008 | 0x0080):
                pending.extend(referenced_classes(member.signature or member.descriptor))
        if item.super_name.startswith(APP_PREFIX):
            pending.append(item.super_name)
    return visited


def inherits(name: str, parents: set[str], classes: dict[str, ClassFile]) -> bool:
    visited = set()
    while name in classes and name not in visited:
        visited.add(name)
        name = classes[name].super_name
        if name in parents:
            return True
    return False


def manifest_constructors(path: Path) -> dict[str, str]:
    attribute = "{http://schemas.android.com/apk/res/android}"
    root = ET.parse(path).getroot()
    result = {}
    for tag in ("application", "activity", "service", "receiver", "provider"):
        for item in root.iter(tag):
            name = item.get(attribute + "name")
            if name:
                result[name.replace(".", "/")] = "()V"
    for item in root.iter("meta-data"):
        name, value = item.get(attribute + "name", ""), item.get(attribute + "value", "")
        if name.startswith("com.google.firebase.components:") or name.startswith("backend:"):
            result[name.split(":", 1)[1].replace(".", "/")] = "()V"
        elif value == "androidx.startup":
            result[name.replace(".", "/")] = "()V"
        elif name == "com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME":
            result[value.replace(".", "/")] = "()V"
        elif name == "androidx.credentials.CREDENTIAL_PROVIDER_KEY":
            result[value.replace(".", "/")] = "(Landroid/content/Context;)V"
    if not result:
        raise ValueError("Merged release manifest has no runtime entry points")
    return result


def validate_contracts(classes: dict[str, ClassFile], dex: dict[str, DexClass],
                       mapping: dict[str, str], manifest: dict[str, str]) -> dict[str, int]:
    failures = []
    for name in DEBUG_ONLY:
        if mapping.get(name, name) in dex:
            failures.append(f"Debug-only component packaged in release: {name}")

    def actual(name: str) -> DexClass | None:
        return dex.get(mapping.get(name, name))

    def constructor(name: str, signature: str) -> None:
        item = actual(name)
        if mapping.get(name, name) != name:
            failures.append(f"Runtime class name changed: {name}")
        expected = mapped_signature(signature, mapping)
        if item is None or not any(m.name == "<init>" and m.descriptor == expected and m.access & 1 for m in item.methods):
            failures.append(f"Missing public constructor {name}{signature}")

    workers = {n for n in classes if inherits(n, WORK_BASES, classes) and not classes[n].access & 0x0400}
    if not workers:
        raise ValueError("No project workers found before R8")
    for name in workers:
        constructor(name, "(Landroid/content/Context;Landroidx/work/WorkerParameters;)V")
    for name in INPUT_MERGERS:
        constructor(name, "()V")
    for name, signature in manifest.items():
        constructor(name, signature)
    legacy = {n for n in classes if n.startswith(APP_PREFIX + "core/data/service/") and "$" not in n}
    for name in legacy:
        constructor(name, "()V")
    databases = {n for n in classes if inherits(n, {"androidx/room/RoomDatabase"}, classes) and n.endswith("_Impl")}
    if len(databases) < 2:
        raise ValueError("Both generated Room implementations must be present before R8")
    for name in databases:
        constructor(name, "()V")

    graph = json_graph(GSON_ROOTS, classes)
    field_count = 0
    for name in sorted(graph):
        source, target = classes[name], actual(name)
        if target is None:
            failures.append(f"Gson model removed: {name}")
            continue
        if source.access & 0x4000:
            expected = {m.name for m in source.fields if m.access & 0x4000}
            if not expected <= {m.name for m in target.fields}:
                failures.append(f"Gson enum constants changed: {name}")
            continue
        for member in source.fields:
            if member.access & (0x0008 | 0x0080):
                continue
            field_count += 1
            if not any(m.name == member.name and m.descriptor == mapped_signature(member.descriptor, mapping)
                       for m in target.fields):
                failures.append(f"Stored JSON field removed/renamed: {name}.{member.name}")
            if member.signature and target.field_signatures.get(member.name) != mapped_signature(member.signature, mapping):
                failures.append(f"Gson field generic signature lost: {name}.{member.name}")
        if any(m.name == "<init>" and m.descriptor == "()V" and m.access & 1 for m in source.methods):
            if not any(m.name == "<init>" and m.descriptor == "()V" and m.access & 1 for m in target.methods):
                failures.append(f"Gson default-value constructor removed: {name}")

    tokens = {n for n in classes if inherits(n, {"com/google/gson/reflect/TypeToken"}, classes)}
    for name in tokens:
        item = actual(name)
        if item is None or item.signature != mapped_signature(classes[name].signature, mapping):
            failures.append(f"Gson TypeToken generic signature lost: {name}")

    api, api_dex = classes.get(API), actual(API)
    if api is None or api_dex is None:
        failures.append("Retrofit API removed")
        api_methods = []
    else:
        api_methods = [m for m in api.methods if m.signature and not m.access & 0x0008]
        for member in api_methods:
            if api_dex.method_signatures.get(member.name) != mapped_signature(member.signature, mapping):
                failures.append(f"Retrofit generic response type lost: {member.name}")
            annotation_types = {" L" + mapping.get("retrofit2/http/" + n, "retrofit2/http/" + n) + ";" for n in HTTP_METHODS}
            if not any(any(kind in line for kind in annotation_types) for line in api_dex.annotations.get(member.name, [])):
                failures.append(f"Retrofit endpoint annotation lost: {member.name}")
            if "RuntimeVisibleParameterAnnotations" in member.attributes and not api_dex.parameter_annotations.get(member.name):
                failures.append(f"Retrofit parameter annotations lost: {member.name}")
    serializer_roots = set().union(*(referenced_classes(m.signature) for m in api_methods)) if api_methods else set()
    serializers = json_graph({n for n in serializer_roots if n.startswith(APP_PREFIX)} | WIDGET_MODELS, classes)
    serializer_count = 0
    for name in serializers:
        companion_name = name + "$Companion"
        source = classes.get(companion_name)
        if source is None or not any(m.name == "serializer" for m in source.methods):
            continue
        serializer_count += 1
        companion, owner = actual(companion_name), actual(name)
        if companion is None or not any(m.name == "serializer" and m.access & 1 for m in companion.methods):
            failures.append(f"Dynamic kotlinx serializer missing: {name}")
        if owner is None or not any(m.name == "Companion" and m.access & 0x0008 for m in owner.fields):
            failures.append(f"Dynamic serializer Companion field missing: {name}")
    if failures:
        raise ValueError("\n".join(failures))
    return {"workers": len(workers), "input_mergers": len(INPUT_MERGERS), "manifest_entrypoints": len(manifest),
            "legacy_components": len(legacy), "room_databases": len(databases), "gson_models": len(graph),
            "json_fields": field_count, "type_tokens": len(tokens), "api_methods": len(api_methods),
            "dynamic_serializers": serializer_count}


def sdk_tool(name: str, sdk: Path) -> Path:
    candidates = list((sdk / "build-tools").glob(f"*/{name}"))
    if not candidates:
        raise ValueError(f"Android SDK build-tools/{name} missing")
    return max(candidates, key=lambda p: tuple(int(v) for v in re.findall(r"[0-9]+", p.parent.name)))


def literal_uri_resources(root: Path) -> set[str]:
    """Named resource URIs are runtime lookups, invisible to code/resource shrinking."""
    names = set()
    modules = [root / "app", *sorted((root / "core").glob("*")), *sorted((root / "feature").glob("*"))]
    pattern = re.compile(r'android\.resource://[^"\r\n]+/((?:raw|drawable|font)/[a-z0-9_]+)')
    for module in modules:
        for variant in ("main", "release"):
            for source in (module / "src" / variant).rglob("*.kt"):
                names.update(pattern.findall(source.read_text(encoding="utf-8")))
    return names


def validate_raw_uri_payloads(artifact: Path, root: Path, names: set[str]) -> int:
    """Check actual audio bytes in APK and AAB, not only a resource-table name."""
    modules = [root / "app", *sorted((root / "core").glob("*")), *sorted((root / "feature").glob("*"))]
    with zipfile.ZipFile(artifact) as archive:
        for name in sorted(n for n in names if n.startswith("raw/")):
            sources = []
            for module in modules:
                release = list((module / "src/release/res").glob(name + ".*"))
                sources.extend(release or (module / "src/main/res").glob(name + ".*"))
            if not sources:
                raise ValueError("Named raw resource source missing: " + name)
            for source in sources:
                data = source.read_bytes()
                candidates = [entry for entry in archive.infolist()
                              if entry.filename.endswith(source.suffix) and entry.file_size == len(data)]
                if not data or not any(hashlib.sha256(archive.read(entry)).digest() == hashlib.sha256(data).digest()
                                       for entry in candidates):
                    raise ValueError("Named raw resource payload removed: " + name)
    return len([name for name in names if name.startswith("raw/")])


def validate_resources(artifact: Path, root: Path, aapt2: Path) -> int:
    expected = {"font/google_sans_flex_variable"}
    expected.update("drawable/" + p.stem for p in (root / "core/designsystem/src/main/res/drawable").glob("pod_cover_*"))
    if len(expected) < 2:
        raise ValueError("Dynamic onboarding resource inventory is missing")
    expected.update(literal_uri_resources(root))
    result = subprocess.run([str(aapt2), "dump", "resources", str(artifact)], capture_output=True, text=True, check=True)
    present = set(re.findall(r"\b(?:[\w.]+:)?((?:drawable|font|raw)/[\w]+)", result.stdout))
    missing = sorted(expected - present)
    if missing:
        raise ValueError("Dynamically looked-up resources removed: " + ", ".join(missing))
    return len(expected)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifacts", type=Path, nargs="+")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--sdk", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    sdk = args.sdk or Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or "")
    try:
        classes = load_project_classes(root)
        mapping = read_mapping(root / "app/build/outputs/mapping/release/mapping.txt")
        manifest = manifest_constructors(root / "app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml")
        report = {}
        for artifact in args.artifacts:
            dex, size = inspect_archive(artifact, sdk_tool("dexdump", sdk))
            counts = validate_contracts(classes, dex, mapping, manifest)
            counts["raw_uri_payloads"] = validate_raw_uri_payloads(artifact, root, literal_uri_resources(root))
            if artifact.suffix == ".apk":
                counts["dynamic_resources"] = validate_resources(artifact, root, sdk_tool("aapt2", sdk))
            counts.update(dex_classes=len(dex), dex_bytes=size, artifact_bytes=artifact.stat().st_size)
            report[artifact.name] = counts
            print(f"{artifact.name}: release contracts passed {json.dumps(counts, sort_keys=True)}")
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    except (OSError, ValueError, subprocess.SubprocessError, ET.ParseError, zipfile.BadZipFile) as error:
        parser.exit(1, f"Release contract check failed: {error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
