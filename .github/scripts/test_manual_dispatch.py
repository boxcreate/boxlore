"""Hermetic sender tests: no Firebase account and no real messages."""

import json
import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import manual_dispatch as sender


def args(*extra):
    parsed = sender.argument_parser().parse_args([
        "--title", "boxlore test", "--body", "First line\\nSecond line", "--type", "both", *extra,
    ])
    if parsed.dry_run == "false" and parsed.preview_only == "false":
        parsed.approval_digest = sender.plan_digest(sender.notification_plan(parsed))
    return parsed


class ManualDispatchTest(unittest.TestCase):
    def test_offline_preview_never_calls_transport(self):
        transport = Mock(side_effect=AssertionError("Preview must not send"))
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "preview.json"
            result = sender.send_notification(args("--preview-only", "true", "--preview-output", str(output)), sender=transport)
            self.assertEqual(json.loads(output.read_text())["mode"], "offline-preview")
            self.assertFalse(result["sent"])
        transport.assert_not_called()

    def test_test_mode_rejects_both_production_audiences_before_transport(self):
        transport = Mock()
        for topic in ("all_users", "prod_users"):
            with self.subTest(topic=topic), self.assertRaisesRegex(ValueError, "only permits debug_users"):
                sender.send_notification(args("--test-mode", "true", "--target", topic), sender=transport)
        transport.assert_not_called()

    def test_debug_test_message_and_dry_run_use_exact_transport_settings(self):
        for dry_run in ("true", "false"):
            with self.subTest(dry_run=dry_run):
                transport = Mock(return_value="fake-message-id")
                result = sender.send_notification(args("--test-mode", "true", "--target", "debug_users", "--dry-run", dry_run), sender=transport)
                self.assertEqual(transport.call_args.args[0]["topic"], "debug_users")
                self.assertEqual(transport.call_args.args[0]["dry_run"], dry_run == "true")
                self.assertEqual(result["data"]["body"], "First line\nSecond line")

    def test_existing_manual_contract_retains_all_optional_keys(self):
        plan = sender.notification_plan(args("--route", "boxlore://podcast/123", "--image", "https://example.invalid/cover.png", "--sound", "silent", "--action-label", "Open show", "--show-action-in-push", "false", "--category", "TIP", "--collapse-key", "manual_tip"))
        self.assertEqual(plan["topic"], "all_users")
        self.assertEqual(plan["collapse_key"], "manual_tip")
        self.assertEqual(plan["data"]["action_label"], "Open show")
        self.assertEqual(plan["data"]["show_action_in_push"], "false")
        self.assertEqual(plan["data"]["category"], "TIP")
        self.assertEqual(plan["data"]["route"], "boxlore://podcast/123")
        self.assertIn("image", plan["data"])

    def test_unicode_size_limit_counts_utf8_keys_and_values(self):
        arguments = args()
        arguments.body = "é" * 1100
        with self.assertRaisesRegex(ValueError, "Shorten the message"):
            sender.notification_plan(arguments)
        arguments.body = "x"
        base = sender.notification_plan(arguments)["payload_bytes"] - 1
        arguments.body = "x" * (2048 - base)
        self.assertEqual(sender.notification_plan(arguments)["payload_bytes"], 2048)
        arguments.body += "x"
        with self.assertRaises(ValueError):
            sender.notification_plan(arguments)

    def test_invalid_controls_fail_closed(self):
        for field, value in (("dry_run", "TRUE"), ("test_mode", "yes"), ("show_action_in_push", "maybe"), ("type", "popup"), ("target", "unknown"), ("sound", "loud"), ("title", " ")):
            with self.subTest(field=field), self.assertRaises(ValueError):
                arguments = args()
                setattr(arguments, field, value)
                sender.notification_plan(arguments)

    def test_firebase_adapter_preserves_dry_run_and_legacy_payload(self):
        # Fake the SDK boundary as well as the higher-level transport: no provider I/O.
        firebase = Mock()
        credentials, messaging = Mock(), Mock()
        firebase.credentials, firebase.messaging = credentials, messaging
        messaging.send.return_value = "fake-provider-id"
        modules = {"firebase_admin": firebase, "firebase_admin.credentials": credentials, "firebase_admin.messaging": messaging}
        arguments = args("--dry-run", "true", "--collapse-key", "test_release")
        with patch.dict(sys.modules, modules), patch.dict(os.environ, {"FIREBASE_CREDENTIALS": '{"project_id":"offline-test"}'}):
            result = sender.send_notification(arguments)
        self.assertEqual(result["response"], "fake-provider-id")
        self.assertTrue(messaging.send.call_args.kwargs["dry_run"])
        self.assertEqual(messaging.Message.call_args.kwargs["data"]["type"], "both")
        self.assertEqual(messaging.Message.call_args.kwargs["topic"], "all_users")
        messaging.AndroidConfig.assert_called_once_with(collapse_key="test_release")


if __name__ == "__main__":
    unittest.main()
