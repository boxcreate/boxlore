"""Read pre-optimization JVM contracts without loading Android classes."""

from __future__ import annotations

import struct
import zipfile
from dataclasses import dataclass, field
from pathlib import Path


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.offset = 0

    def take(self, size: int) -> bytes:
        end = self.offset + size
        if end > len(self.data):
            raise ValueError("Truncated class file")
        value = self.data[self.offset:end]
        self.offset = end
        return value

    def u1(self) -> int:
        return self.take(1)[0]

    def u2(self) -> int:
        return struct.unpack(">H", self.take(2))[0]

    def u4(self) -> int:
        return struct.unpack(">I", self.take(4))[0]


@dataclass
class Member:
    name: str
    descriptor: str
    access: int
    attributes: dict[str, bytes] = field(default_factory=dict)
    signature: str = ""


@dataclass
class ClassFile:
    name: str
    super_name: str
    access: int
    fields: list[Member]
    methods: list[Member]
    signature: str


def parse_class(data: bytes) -> ClassFile:
    reader = Reader(data)
    if reader.u4() != 0xCAFEBABE:
        raise ValueError("Invalid JVM class file")
    reader.take(4)  # minor/major version
    pool: list[str | int | None] = [None] * reader.u2()
    index = 1
    sizes = {3: 4, 4: 4, 5: 8, 6: 8, 8: 2, 9: 4, 10: 4, 11: 4,
             12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < len(pool):
        tag = reader.u1()
        if tag == 1:
            pool[index] = reader.take(reader.u2()).decode("utf-8", errors="replace")
        elif tag == 7:
            pool[index] = reader.u2()
        elif tag in sizes:
            reader.take(sizes[tag])
            if tag in (5, 6):
                index += 1
        else:
            raise ValueError(f"Unsupported constant-pool tag {tag}")
        index += 1

    def utf8(position: int) -> str:
        value = pool[position]
        if not isinstance(value, str):
            raise ValueError("Invalid UTF-8 constant-pool reference")
        return value

    def class_name(position: int) -> str:
        if position == 0:
            return ""
        value = pool[position]
        if not isinstance(value, int):
            raise ValueError("Invalid class constant-pool reference")
        return utf8(value)

    def attributes() -> dict[str, bytes]:
        result = {}
        for _ in range(reader.u2()):
            name = utf8(reader.u2())
            result[name] = reader.take(reader.u4())
        return result

    def signature(attrs: dict[str, bytes]) -> str:
        return utf8(Reader(attrs["Signature"]).u2()) if "Signature" in attrs else ""

    def members() -> list[Member]:
        result = []
        for _ in range(reader.u2()):
            access, name, descriptor = reader.u2(), utf8(reader.u2()), utf8(reader.u2())
            attrs = attributes()
            result.append(Member(name, descriptor, access, attrs, signature(attrs)))
        return result

    access, name, super_name = reader.u2(), class_name(reader.u2()), class_name(reader.u2())
    reader.take(2 * reader.u2())  # interfaces
    fields, methods = members(), members()
    attrs = attributes()
    return ClassFile(name, super_name, access, fields, methods, signature(attrs))


def load_project_classes(root: Path, app_variant: str = "release") -> dict[str, ClassFile]:
    result: dict[str, ClassFile] = {}
    modules = [root / "app", *sorted((root / "core").glob("*")), *sorted((root / "feature").glob("*"))]
    for module in modules:
        jars = sorted((module / "build/intermediates/runtime_library_classes_jar/release").rglob("*.jar"))
        for jar in jars:
            with zipfile.ZipFile(jar) as archive:
                for entry in archive.namelist():
                    if entry.endswith(".class"):
                        item = parse_class(archive.read(entry))
                        result[item.name] = item
    # The application is not published as a library jar.
    task_variant = app_variant[0].upper() + app_variant[1:]
    for directory in [root / f"app/build/intermediates/javac/{app_variant}/compile{task_variant}JavaWithJavac/classes",
                      root / f"app/build/intermediates/built_in_kotlinc/{app_variant}/compile{task_variant}Kotlin/classes"]:
        for path in sorted(directory.rglob("*.class")):
            item = parse_class(path.read_bytes())
            result[item.name] = item
    if "cx/aswin/boxlore/core/network/BoxLoreApi" not in result or "cx/aswin/boxlore/LegacyWorkerFactory" not in result:
        raise ValueError(f"Pre-R8 {app_variant} classes missing; build assembleRelease and bundlePlayRelease first")
    return result
