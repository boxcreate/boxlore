"""Local-only updater fixture. Never publishes a release or contacts Firebase.

Install a signed updateTest build first. Build its higher-code successor with
-PboxloreUpdateTestVersionCode=<code>, then serve that APK and reverse adb port 8765.
"""
import argparse
import hashlib
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

DEFAULT_NOTES = 'Isolated update test. No release was published and no alert was sent.'


def manifest(apk, code, state='available', notes=DEFAULT_NOTES, upcoming=""):
    value = dict(schemaVersion=1, packageName='cx.aswin.boxlore', versionCode=code,
                versionName=f'Test {code}', minSdk=99 if state == 'incompatible' else 31,
                apkUrl='http://127.0.0.1:8765/candidate.apk',
                apkSha256='0' * 64 if state == 'corrupt' else hashlib.sha256(apk.read_bytes()).hexdigest(),
                apkBytes=apk.stat().st_size, notesUrl='http://127.0.0.1:8765/notes',
                notes=notes, upcoming=upcoming, testOnly=True)
    if len(notes.encode('utf-16-le')) // 2 > 24_000:
        raise ValueError('Release notes exceed the app limit of 24,000 characters')
    if len(json.dumps(value, ensure_ascii=False).encode('utf-8')) > 64 * 1024:
        raise ValueError('The update manifest exceeds the app limit of 64 KiB')
    return value


def handler(apk, code, state, notes=DEFAULT_NOTES, upcoming=""):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path == '/update.json':
                if state == 'unavailable':
                    self.send_error(404); return
                data = json.dumps(manifest(apk, code, state, notes, upcoming), ensure_ascii=False).encode('utf-8')
                self.send_response(200); self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(data))); self.end_headers(); self.wfile.write(data)
            elif self.path == '/candidate.apk':
                size = apk.stat().st_size; start = 0
                if self.headers.get('Range'):
                    try: start = int(self.headers['Range'].removeprefix('bytes=').removesuffix('-'))
                    except ValueError: self.send_error(400); return
                    if start >= size or start < 0: self.send_error(416); return
                self.send_response(206 if start else 200)
                if start: self.send_header('Content-Range', f'bytes {start}-{size-1}/{size}')
                self.send_header('Content-Type', 'application/vnd.android.package-archive')
                self.send_header('Content-Length', str(size-start)); self.end_headers()
                try:
                    with apk.open('rb') as stream:
                        stream.seek(start)
                        while chunk := stream.read(32768): self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError): pass
            elif self.path == '/notes':
                data = notes.encode('utf-8')
                self.send_response(200)
                self.send_header('Content-Type', 'text/plain; charset=utf-8')
                self.send_header('Content-Length', str(len(data)))
                self.end_headers(); self.wfile.write(data)
            else: self.send_error(404)
    return Handler


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--version-code', type=int, required=True)
    parser.add_argument('--state', choices=('available','unavailable','incompatible','corrupt'), default='available')
    parser.add_argument('--notes-file', type=Path, help='UTF-8 sample release notes for local scrolling tests')
    parser.add_argument('--upcoming-file', type=Path, help='UTF-8 local unreleased preview')
    args = parser.parse_args()
    if not args.apk.is_file() or args.version_code <= 0: parser.error('Use an existing signed test APK and a positive version code')
    try:
        notes = args.notes_file.read_text(encoding='utf-8') if args.notes_file else DEFAULT_NOTES
        upcoming = args.upcoming_file.read_text(encoding='utf-8') if args.upcoming_file else ''
        if len(upcoming.encode('utf-16-le')) // 2 > 6_000: raise ValueError('Upcoming preview exceeds 6,000 characters')
        manifest(args.apk, args.version_code, args.state, notes, upcoming)
    except (OSError, UnicodeError, ValueError) as error:
        parser.error(str(error))
    print('Local test feed: http://127.0.0.1:8765/update.json; use adb reverse tcp:8765 tcp:8765', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 8765), handler(args.apk, args.version_code, args.state, notes, upcoming)).serve_forever()
