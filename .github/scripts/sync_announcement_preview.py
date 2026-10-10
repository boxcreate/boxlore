"""Sync the app font for the browser preview; native Compose and browser markup remain independent."""
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def sync(check=False):
    sources = {'font.ttf': ROOT / 'core/designsystem/src/main/res/font/google_sans_flex_variable.ttf'}
    target = ROOT / 'admin-panel/public/notifyappusers/announcements'
    if not check:
        target.mkdir(parents=True, exist_ok=True)
    for name, source in sources.items():
        destination = target / name
        if check:
            if not destination.exists() or destination.read_bytes() != source.read_bytes():
                raise ValueError(f'Announcement preview differs: {name}; run sync_announcement_preview.py')
        else:
            destination.write_bytes(source.read_bytes())

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true')
    sync(parser.parse_args().check)
