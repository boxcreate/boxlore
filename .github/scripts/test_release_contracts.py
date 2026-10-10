"""Hermetic regressions for the release-artifact gate (no Android runtime)."""

from __future__ import annotations

import copy
import struct
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import verify_release_contracts as check
from release_classfile import ClassFile, Member, Reader, parse_class
from release_dex import DEX_ENTRY, DexClass, inspect_archive, mapped_signature, parse_dump, read_mapping


def constructor(signature="()V", access=0x10001):
    return Member("<init>", signature, access)


def fixture():
    model = check.APP_PREFIX + "core/model/Episode"
    worker = check.APP_PREFIX + "fcm/TestWorker"
    token = check.APP_PREFIX + "core/database/ExampleToken"
    source = {
        model: ClassFile(model, "java/lang/Object", 1, [Member("title", "Ljava/lang/String;", 0x12)], [constructor()], ""),
        worker: ClassFile(worker, "androidx/work/CoroutineWorker", 1, [], [constructor("(Landroid/content/Context;Landroidx/work/WorkerParameters;)V")], ""),
        token: ClassFile(token, "com/google/gson/reflect/TypeToken", 1, [], [constructor()], "Lcom/google/gson/reflect/TypeToken<Ljava/util/List<L" + model + ";>;>;"),
        check.API: ClassFile(check.API, "java/lang/Object", 0x601, [],
                             [Member("getEpisode", "()Lretrofit2/Call;", 0x401,
                                     {"RuntimeVisibleParameterAnnotations": b"fixture"},
                                     "()Lretrofit2/Call<L" + model + ";>;")], ""),
    }
    for name in (check.APP_PREFIX + "core/database/Db_Impl", check.APP_PREFIX + "core/ranking/database/Db_Impl"):
        source[name] = ClassFile(name, "androidx/room/RoomDatabase", 1, [], [constructor()], "")
    companion = model + "$Companion"
    source[model].fields.append(Member("Companion", "L" + companion + ";", 0x19))
    source[companion] = ClassFile(companion, "java/lang/Object", 1, [], [Member("serializer", "()Lkotlinx/serialization/KSerializer;", 1)], "")
    dex = {name: DexClass(name, item.super_name, copy.deepcopy(item.fields), copy.deepcopy(item.methods), item.signature)
           for name, item in source.items()}
    for name in check.INPUT_MERGERS:
        dex[name] = DexClass(name, "androidx/work/InputMerger", methods=[constructor()])
    dex[check.API].method_signatures["getEpisode"] = source[check.API].methods[0].signature
    dex[check.API].annotations["getEpisode"] = ["VISIBILITY_RUNTIME Lretrofit2/http/GET; value=episode"]
    dex[check.API].parameter_annotations["getEpisode"] = ["VISIBILITY_RUNTIME Lretrofit2/http/Query;"]
    return model, worker, token, source, dex


class ContractTest(unittest.TestCase):
    def setUp(self):
        self.model, self.worker, self.token, self.source, self.dex = fixture()
        self.roots = patch.object(check, "GSON_ROOTS", {self.model})
        self.widgets = patch.object(check, "WIDGET_MODELS", set())
        self.roots.start()
        self.widgets.start()
        self.addCleanup(self.roots.stop)
        self.addCleanup(self.widgets.stop)

    def validate(self, mapping=None):
        return check.validate_contracts(self.source, self.dex, mapping or {}, {})

    def test_complete_release_contracts_pass(self):
        result = self.validate()
        self.assertEqual(1, result["workers"])
        self.assertEqual(1, result["json_fields"])
        self.assertEqual(1, result["dynamic_serializers"])

    def test_published_failure_class_kept_constructor_removed(self):
        for name in check.INPUT_MERGERS:
            with self.subTest(name=name):
                saved = self.dex[name].methods
                self.dex[name].methods = [Member("merge", "()V", 1)]
                with self.assertRaisesRegex(ValueError, "Missing public constructor"):
                    self.validate()
                self.dex[name].methods = saved

    def test_private_or_wrong_constructor_fails(self):
        name = next(iter(check.INPUT_MERGERS))
        for ctor in [constructor(access=0x10002), constructor("(I)V"), Member("<clinit>", "()V", 0x10008)]:
            self.dex[name].methods = [ctor]
            with self.assertRaisesRegex(ValueError, "Missing public constructor"):
                self.validate()

    def test_worker_renaming_is_an_upgrade_failure(self):
        self.dex["a"] = self.dex.pop(self.worker)
        with self.assertRaisesRegex(ValueError, "Runtime class name changed"):
            self.validate({self.worker: "a"})

    def test_stored_json_field_renaming_fails(self):
        self.dex[self.model].fields[0].name = "a"
        with self.assertRaisesRegex(ValueError, "Stored JSON field removed/renamed"):
            self.validate()

    def test_default_constructor_is_needed_for_json_defaults(self):
        self.dex[self.model].methods = []
        with self.assertRaisesRegex(ValueError, "Gson default-value constructor removed"):
            self.validate()

    def test_transient_and_static_fields_are_not_json_contracts(self):
        self.source[self.model].fields.extend([Member("transient", "I", 0x80), Member("static", "I", 8)])
        self.validate()

    def test_recursive_generic_fields_join_the_json_graph(self):
        child = check.APP_PREFIX + "core/model/Nested"
        self.source[self.model].fields.append(Member("children", "Ljava/util/List;", 1, signature="Ljava/util/List<L" + child + ";>;"))
        self.source[child] = ClassFile(child, "java/lang/Object", 1, [], [], "")
        self.assertEqual({self.model, child}, check.json_graph({self.model}, self.source))

    def test_gson_field_generic_signature_survives_with_mapped_element_type(self):
        child = check.APP_PREFIX + "core/model/Person"
        signature = "Ljava/util/List<L" + child + ";>;"
        self.source[self.model].fields.append(Member("people", "Ljava/util/List;", 0x12, signature=signature))
        self.dex[self.model].fields.append(Member("people", "Ljava/util/List;", 0x12))
        self.source[child] = ClassFile(child, "java/lang/Object", 1, [], [], "")
        self.dex[child] = DexClass(child, "java/lang/Object")
        self.dex[self.model].field_signatures["people"] = signature
        self.validate()
        self.dex["a"] = self.dex.pop(child)
        mapping = {child: "a"}
        self.dex[self.model].field_signatures["people"] = check.mapped_signature(signature, mapping)
        self.validate(mapping)
        for erased in (None, "", "Ljava/util/List;"):
            with self.subTest(signature=erased):
                if erased is None:
                    self.dex[self.model].field_signatures.pop("people", None)
                else:
                    self.dex[self.model].field_signatures["people"] = erased
                with self.assertRaisesRegex(ValueError, "Gson field generic signature lost"):
                    self.validate(mapping)

    def test_type_token_generic_erasure_or_removal_fails(self):
        self.dex[self.token].signature = "Lcom/google/gson/reflect/TypeToken;"
        with self.assertRaisesRegex(ValueError, "TypeToken generic signature lost"):
            self.validate()
        del self.dex[self.token]
        with self.assertRaisesRegex(ValueError, "TypeToken generic signature lost"):
            self.validate()

    def test_network_loading_breaks_if_call_generic_type_is_erased(self):
        self.dex[check.API].method_signatures["getEpisode"] = "()Lretrofit2/Call;"
        with self.assertRaisesRegex(ValueError, "Retrofit generic response type lost"):
            self.validate()

    def test_retrofit_endpoint_and_parameter_annotations_required(self):
        self.dex[check.API].annotations = {}
        with self.assertRaisesRegex(ValueError, "Retrofit endpoint annotation lost"):
            self.validate()
        self.dex[check.API].annotations["getEpisode"] = ["VISIBILITY_RUNTIME Lretrofit2/http/GET;"]
        self.dex[check.API].parameter_annotations = {}
        with self.assertRaisesRegex(ValueError, "Retrofit parameter annotations lost"):
            self.validate()

    def test_obfuscated_annotation_names_are_valid(self):
        self.dex[check.API].annotations["getEpisode"] = ["VISIBILITY_RUNTIME Lab;"]
        self.validate({"retrofit2/http/GET": "ab"})

    def test_dynamic_serializer_and_companion_are_both_required(self):
        self.dex[self.model + "$Companion"].methods = []
        with self.assertRaisesRegex(ValueError, "Dynamic kotlinx serializer missing"):
            self.validate()
        self.dex[self.model + "$Companion"].methods = self.source[self.model + "$Companion"].methods
        self.dex[self.model].fields = self.dex[self.model].fields[:1]
        with self.assertRaisesRegex(ValueError, "Companion field missing"):
            self.validate()

    def test_missing_pre_r8_inventory_fails_closed(self):
        self.source.pop(self.worker)
        with self.assertRaisesRegex(ValueError, "No project workers"):
            self.validate()

    def test_debug_sdk_or_test_receiver_is_rejected(self):
        for name in check.DEBUG_ONLY:
            with self.subTest(name=name):
                self.dex[name] = DexClass(name)
                with self.assertRaisesRegex(ValueError, "Debug-only component packaged"):
                    self.validate()
                del self.dex[name]


class ParserTest(unittest.TestCase):
    def test_annotations_preceding_descriptor_and_same_class_index(self):
        dump = """Class #3 annotations:
Annotations on class
  VISIBILITY_SYSTEM Ldalvik/annotation/Signature; value={ "LX<" "LY;" ">;" }
Annotations on field #7 'items'
  VISIBILITY_SYSTEM Ldalvik/annotation/Signature; value={ "Ljava/util/List<" "LY;" ">;" }
Annotations on method #12 'load'
  VISIBILITY_SYSTEM Ldalvik/annotation/Signature; value={ "()" "LX<LY;>;" }
  VISIBILITY_RUNTIME Lretrofit2/http/GET; value="episode"
Annotations on method #12 'load' parameters
  VISIBILITY_RUNTIME Lretrofit2/http/Query; value="id"
Class #3 -
  Class descriptor : 'LExample;'
  Superclass : 'LObject;'
  Direct methods -
    #0 : (in LExample;)
      name : '<init>'
      type : '()V'
      access : 0x10001 (PUBLIC CONSTRUCTOR)
Class #4 -
  Class descriptor : 'LOther;'
  Direct methods -
    #0 : (in LOther;)
      name : 'merge'
      type : '()V'
      access : 0x1 (PUBLIC)
"""
        result = parse_dump(dump.splitlines())
        self.assertEqual("LX<LY;>;", result["Example"].signature)
        self.assertEqual("Ljava/util/List<LY;>;", result["Example"].field_signatures["items"])
        self.assertEqual("()LX<LY;>;", result["Example"].method_signatures["load"])
        self.assertIn("load", result["Example"].parameter_annotations)
        self.assertFalse(result["Other"].method_signatures)
        self.assertFalse(result["Other"].field_signatures)
        self.assertEqual("merge", result["Other"].methods[0].name)

    def test_mapping_is_applied_inside_generics(self):
        self.assertEqual("Ljava/util/List<La;>;", mapped_signature("Ljava/util/List<Lexample/Model;>;", {"example/Model": "a"}))

    def test_mapping_headers_ignore_methods_and_comments(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "mapping.txt"
            path.write_text("# comment\nexample.Model -> a:\n    int field -> b\n")
            self.assertEqual({"example/Model": "a"}, read_mapping(path))

    def test_empty_archive_fails_without_invoking_sdk(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "empty.apk"
            with zipfile.ZipFile(path, "w"):
                pass
            with self.assertRaisesRegex(ValueError, "no DEX"):
                inspect_archive(path, Path("not-a-tool"))

    def test_apk_and_bundle_dex_entry_selection(self):
        for name in ["classes.dex", "classes2.dex", "base/dex/classes.dex", "feature/dex/classes3.dex"]:
            self.assertTrue(DEX_ENTRY.fullmatch(name))
        for name in ["assets/classes.dex", "classes.dex.bak", "base/classes.dex"]:
            self.assertFalse(DEX_ENTRY.fullmatch(name))

    def test_truncated_and_nonclass_data_fail(self):
        with self.assertRaises(ValueError):
            Reader(b"\x00").u2()
        with self.assertRaises(ValueError):
            parse_class(b"not-a-class")

    def test_classfile_names_fields_and_signatures(self):
        strings = ["Example", "java/lang/Object", "title", "Ljava/lang/String;", "Signature", "Ljava/util/List<Ljava/lang/String;>;"]
        pool = b"".join(b"\x01" + struct.pack(">H", len(s)) + s.encode() for s in strings)
        pool += b"\x07\x00\x01\x07\x00\x02"
        data = struct.pack(">IHHH", 0xCAFEBABE, 0, 61, 9) + pool
        data += struct.pack(">HHHHH", 1, 7, 8, 0, 1)
        data += struct.pack(">HHHH", 0x12, 3, 4, 1) + struct.pack(">HIH", 5, 2, 6)
        data += struct.pack(">HH", 0, 0)
        result = parse_class(data)
        self.assertEqual("Example", result.name)
        self.assertEqual("title", result.fields[0].name)
        self.assertEqual(strings[-1], result.fields[0].signature)

    def test_manifest_discovers_metadata_providers(self):
        xml = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:name="example.App">
        <meta-data android:name="com.google.firebase.components:example.Registrar" android:value="registrar"/>
        <meta-data android:name="androidx.credentials.CREDENTIAL_PROVIDER_KEY" android:value="example.Credentials"/>
        </application></manifest>'''
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "manifest.xml"
            path.write_text(xml)
            expected = check.manifest_constructors(path)
        self.assertEqual("()V", expected["example/Registrar"])
        self.assertEqual("(Landroid/content/Context;)V", expected["example/Credentials"])

    def test_missing_dynamic_resources_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            artwork = root / "core/designsystem/src/main/res/drawable"
            artwork.mkdir(parents=True)
            (artwork / "pod_cover_0.jpg").touch()
            with patch.object(check.subprocess, "run", return_value=type("Output", (), {"stdout": "font/google_sans_flex_variable"})()):
                with self.assertRaisesRegex(ValueError, "pod_cover_0"):
                    check.validate_resources(root / "app.apk", root, Path("aapt2"))

    def test_literal_resource_uris_are_discovered_without_debug_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for variant in ("main", "release", "debug"):
                directory = root / "app/src" / variant / "java/example"
                directory.mkdir(parents=True)
                (directory / "Sound.kt").write_text('val sound = "android.resource://$packageName/raw/' + variant + '_chime"')
            self.assertEqual({"raw/main_chime", "raw/release_chime"}, check.literal_uri_resources(root))

    def test_named_notification_sound_payload_required_in_apk_and_bundle(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            raw = root / "app/src/main/res/raw"
            raw.mkdir(parents=True)
            (raw / "chime.wav").write_bytes(b"RIFF-test-audio")
            for path in ("res/short.wav", "base/res/raw/chime.wav"):
                artifact = root / ("app.aab" if path.startswith("base/") else "app.apk")
                with zipfile.ZipFile(artifact, "w") as archive:
                    archive.writestr(path, b"x" * len(b"RIFF-test-audio"))
                with self.assertRaisesRegex(ValueError, "raw/chime"):
                    check.validate_raw_uri_payloads(artifact, root, {"raw/chime"})
                with zipfile.ZipFile(artifact, "w") as archive:
                    archive.writestr(path, b"RIFF-test-audio")
                self.assertEqual(1, check.validate_raw_uri_payloads(artifact, root, {"raw/chime"}))

    def test_release_sound_overrides_main_sound(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for variant in ("main", "release"):
                raw = root / "app/src" / variant / "res/raw"
                raw.mkdir(parents=True)
                (raw / "chime.wav").write_bytes(variant.encode())
            artifact = root / "app.apk"
            with zipfile.ZipFile(artifact, "w") as archive:
                archive.writestr("res/short.wav", b"release")
            self.assertEqual(1, check.validate_raw_uri_payloads(artifact, root, {"raw/chime"}))

    def test_notification_sound_name_cannot_disappear_from_resource_table(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            artwork = root / "core/designsystem/src/main/res/drawable"
            artwork.mkdir(parents=True)
            (artwork / "pod_cover_0.jpg").touch()
            source = root / "app/src/main/java/example"
            source.mkdir(parents=True)
            (source / "Alert.kt").write_text('val sound = "android.resource://$packageName/raw/chime"')
            with patch.object(check.subprocess, "run", return_value=type("Output", (), {
                    "stdout": "font/google_sans_flex_variable drawable/pod_cover_0"})()):
                with self.assertRaisesRegex(ValueError, "raw/chime"):
                    check.validate_resources(root / "app.apk", root, Path("aapt2"))

    def test_both_publication_paths_and_manual_preflight_are_guarded(self):
        """Full R8 verification gates publication; preflight is opt-in only."""
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/changelog-on-merge.yml").read_text()
        self.assertEqual(4, workflow.count("python3 .github/scripts/verify_release_contracts.py"))
        for block in workflow.split("- name: Verify optimized release contracts")[1:]:
            self.assertIn("app-release.apk", block[:450])
            self.assertIn("app-playRelease.aab", block[:600])
        preflight = (root / ".github/workflows/release-contracts.yml").read_text()
        self.assertIn("app-release-unsigned.apk", preflight)
        self.assertIn("uploadCrashlyticsMappingFileRelease", preflight)
        self.assertIn("--app-variant playRelease", preflight)
        self.assertIn("workflow_dispatch:", preflight)
        self.assertNotIn("pull_request:", preflight)
        self.assertNotIn("push:", preflight)

    def test_android_setup_never_requests_removed_tools_package(self):
        """All artifact-build jobs override the pinned action's obsolete default."""
        root = Path(__file__).resolve().parents[2]
        for filename, expected_setups in (
                ("release-contracts.yml", 1), ("changelog-on-merge.yml", 2)):
            with self.subTest(workflow=filename):
                workflow = (root / ".github/workflows" / filename).read_text()
                setups = workflow.split("uses: android-actions/setup-android@")[1:]
                self.assertEqual(expected_setups, len(setups))
                for setup in setups:
                    step = setup.split("\n      - ", 1)[0]
                    self.assertIn("packages: 'platform-tools'", step)


if __name__ == "__main__":
    unittest.main()
