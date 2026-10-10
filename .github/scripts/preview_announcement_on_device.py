"""Render an exported announcement on an isolated updateTest build over ADB.

No Firebase, workflow dispatch, topic message or production release is involved.
The receiver exists only in updateTest and requires Android's shell permission.
Delivery eligibility is tested separately: this local visual preview resets the
release code to 0 so an already-current test device can display the same layout.
"""
import argparse
import json
import shlex
import subprocess
from pathlib import Path

COMPONENT = 'cx.aswin.boxlore/.testing.AnnouncementTestReceiver'
ACTION = 'cx.aswin.boxlore.TEST_ANNOUNCEMENT'


def preview(preview_path, adb='adb', serial=None, runner=subprocess.run):
    value = json.loads(Path(preview_path).read_text(encoding='utf-8'))
    data = value.get('data')
    if not isinstance(data, dict) or not data.get('title') or not data.get('body'):
        raise ValueError('Choose an exported announcement-preview JSON file')
    command = [adb] + (['-s', serial] if serial else [])
    # ADB forwards a shell command. Quote every remote argument so copy remains
    # exact, including spaces, apostrophes and shell metacharacters.
    remote = shlex.join(['am', 'broadcast', '-n', COMPONENT, '-a', ACTION,
                         '--es', 'payload', json.dumps(data, ensure_ascii=False)])
    result = runner(command + ['shell', remote], check=True, text=True, capture_output=True)
    # An absent isolated receiver is a failure, not a successful preview on a normal install.
    if 'result=-1' not in result.stdout or any(word in result.stdout + result.stderr for word in ('Error', 'Exception', 'Permission Denial')):
        raise ValueError('Install the isolated updateTest APK first, then retry this local preview')
    return result.stdout


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--preview', type=Path, required=True)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial')
    args = parser.parse_args()
    try:
        print(preview(args.preview, args.adb, args.serial))
        print('Local preview requested. Open boxlore on the test device; no message was sent to users.')
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f'Preview stopped: {error}\n')
