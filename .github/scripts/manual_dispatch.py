"""Preview, validate or send a legacy-compatible manual announcement.

Preview mode has no Firebase dependency. Test mode only accepts debug_users or test_users;
it never silently redirects a request intended for a production audience.
"""

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path
from urllib.parse import urlparse

TOPIC_PAYLOAD_LIMIT = 2048  # FCM counts UTF-8 data keys and values for topics.
AUDIENCES = ("all_users", "debug_users", "prod_users", "test_users", "direct_users")


def boolean(value, field):
    if value not in ("true", "false"):
        raise ValueError(f"{field} must be true or false")
    return value == "true"


def notification_plan(args):
    """Pure validation and payload construction, shared by previews and sends."""
    topic = getattr(args, "target", "all_users")
    if topic not in AUDIENCES:
        raise ValueError("Choose a supported announcement audience")
    test_mode = boolean(getattr(args, "test_mode", "false"), "test_mode")
    if test_mode and topic not in ("debug_users", "test_users"):
        raise ValueError("Test mode only permits debug_users or test_users. Change the target before retrying.")
    if args.type not in ("push", "in-app", "both"):
        raise ValueError("Message type must be push, in-app or both")
    if not args.title.strip() or not args.body.strip():
        raise ValueError("Add a title and message before previewing or sending")
    if getattr(args, "image", ""):
        image = urlparse(args.image)
        if image.scheme != "https" or not image.netloc or image.username or image.password:
            raise ValueError("Use an HTTPS image URL without embedded credentials")
    sound = getattr(args, "sound", "default")
    if sound not in ("default", "chime", "announcement", "silent"):
        raise ValueError("Choose a supported notification sound")
    data = {
        "title": args.title,
        "body": args.body.replace("\\n", "\n"),
        "type": args.type,
        "sound": sound,
        "action_label": getattr(args, "action_label", "View"),
        "show_action_in_push": getattr(args, "show_action_in_push", "true"),
        "show_action_in_app": getattr(args, "show_action_in_app", "true"),
        "category": getattr(args, "category", "ANNOUNCEMENT"),
    }
    for field in ("show_action_in_push", "show_action_in_app"):
        boolean(data[field], field)
    if getattr(args, "release_url", ""):
        url = urlparse(args.release_url)
        if url.scheme != "https" or url.netloc != "github.com" or url.query or url.fragment or not (url.path == "/boxcreate/boxlore/releases/latest" or url.path.startswith("/boxcreate/boxlore/releases/tag/")):
            raise ValueError("Use a boxlore GitHub release page")
    for field in ("route", "image"):
        if getattr(args, field, ""):
            data[field] = getattr(args, field)
    for field, choices, default in (
        ("presentation", ("compact", "fullscreen"), "compact"),
        ("tone", ("primary", "secondary", "tertiary", "error"), "primary"),
        ("image_style", ("banner", "cover"), "banner"),
    ):
        value = getattr(args, field, default)
        if value not in choices: raise ValueError(f"Choose a supported {field}")
        data[field] = value
    for field in ("release_alert", "include_play"):
        data[field] = str(boolean(getattr(args, field, "false"), field)).lower()
    if data["release_alert"] == "false" and getattr(args, "release_url", ""):
        raise ValueError("Enable Release alert to use a GitHub release page, or clear the release URL")
    if data["release_alert"] == "true" and getattr(args, "release_url", ""):
        data["release_url"] = args.release_url
    data["test_mode"] = str(test_mode).lower()
    code = getattr(args, "release_version_code", "0")
    if not str(code).isdigit() or int(code) > 2147483647: raise ValueError("Release version code must be a nonnegative whole number")
    data["release_version_code"] = str(int(code))
    if topic == "direct_users" and data["include_play"] == "true":
        raise ValueError("Choose all_users to include Google Play installs")
    if data["release_alert"] == "true" and topic not in ("direct_users", "debug_users", "test_users") and data["include_play"] != "true":
        raise ValueError("Release alerts must target direct_users unless Google Play is explicitly included")
    size = sum(len(key.encode("utf-8")) + len(value.encode("utf-8")) for key, value in data.items())
    if size > TOPIC_PAYLOAD_LIMIT:
        raise ValueError(
            f"Message data uses {size} bytes; topic messages allow {TOPIC_PAYLOAD_LIMIT}. "
            "Shorten the message or link to the full notes, then preview again."
        )
    return {
        "topic": topic,
        "data": data,
        "collapse_key": getattr(args, "collapse_key", ""),
        "dry_run": boolean(getattr(args, "dry_run", "false"), "dry_run"),
        "test_mode": test_mode,
        "payload_bytes": size,
    }


def plan_digest(plan):
    return hashlib.sha256(json.dumps(plan, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def _firebase_send(plan):
    # Only the sending path imports/initializes Firebase. Offline previews cannot send.
    import firebase_admin
    from firebase_admin import credentials, messaging

    creds_json = os.environ.get("FIREBASE_CREDENTIALS")
    if not creds_json:
        raise ValueError("FIREBASE_CREDENTIALS is required for provider validation or sending")
    firebase_admin.initialize_app(credentials.Certificate(json.loads(creds_json)))
    android = messaging.AndroidConfig(collapse_key=plan["collapse_key"]) if plan["collapse_key"] else None
    audience = {"condition": "'direct_users' in topics && !('play_users' in topics) && !('test_users' in topics)"} if plan["topic"] == "direct_users" else {"topic": plan["topic"]}
    return messaging.send(
        messaging.Message(data=plan["data"], android=android, **audience),
        dry_run=plan["dry_run"],
    )


def send_notification(args, *, sender=None):
    plan = notification_plan(args)
    if boolean(getattr(args, "preview_only", "false"), "preview_only"):
        preview = {**plan, "approval_digest": plan_digest(plan), "mode": "offline-preview", "sent": False}
        rendered = json.dumps(preview, ensure_ascii=False, indent=2) + "\n"
        output = getattr(args, "preview_output", "")
        if output:
            Path(output).write_text(rendered, encoding="utf-8")
        else:
            print(rendered, end="")
        return preview
    if not plan["dry_run"] and getattr(args, "approval_digest", "") != plan_digest(plan):
        raise ValueError("Review the current preview and approve it before sending. Edited drafts need a new approval.")
    response = (sender or _firebase_send)(plan)
    if plan["dry_run"]:
        print(f"Provider validation passed for '{plan['topic']}'. No message sent. ID: {response}")
    else:
        print(f"FCM accepted the message for '{plan['topic']}'. Device delivery is not confirmed. ID: {response}")
    return {**plan, "mode": "provider-validation" if plan["dry_run"] else "send", "response": response}


def argument_parser():
    parser = argparse.ArgumentParser(description="Preview, validate or send an FCM announcement")
    parser.add_argument("--title", required=True)
    parser.add_argument("--body", required=True)
    parser.add_argument("--type", required=True)
    for name, default in (
        ("route", ""), ("image", ""), ("target", "all_users"),
        ("dry-run", "false"), ("test-mode", "false"), ("preview-only", "false"),
        ("preview-output", ""), ("collapse-key", ""), ("sound", "default"),
        ("presentation", "compact"), ("tone", "primary"), ("image-style", "banner"),
        ("release-url", ""), ("release-alert", "false"), ("include-play", "false"), ("release-version-code", "0"), ("approval-digest", ""),
        ("action-label", "View"), ("show-action-in-push", "true"),
        ("show-action-in-app", "true"), ("category", "ANNOUNCEMENT"),
    ):
        parser.add_argument("--" + name, default=default)
    return parser


if __name__ == "__main__":
    try:
        send_notification(argument_parser().parse_args())
    except (ValueError, OSError) as error:
        print(f"Announcement stopped: {error}", file=sys.stderr)
        sys.exit(1)
