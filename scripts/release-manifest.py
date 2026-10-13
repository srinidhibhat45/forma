#!/usr/bin/env python3
"""Check the actual APK and publish exactly the identity and digest the app verifies."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

apk = Path(sys.argv[1])
code = int(sys.argv[2])
name = sys.argv[3]
aapt = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0/aapt2'
badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(apk)], text=True)
match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
if not match or match.groups() != ('com.forma.health', str(code), name):
    raise SystemExit('The APK does not match the release version and package.')
size = apk.stat().st_size
if not 0 < size <= 100 * 1024 * 1024:
    raise SystemExit('The APK is outside the supported download size.')
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
manifest = {'schemaVersion': 1, 'packageName': 'com.forma.health', 'versionCode': code,
            'versionName': name, 'sha256': sha, 'apkBytes': size}
(apk.parent / 'update.json').write_text(json.dumps(manifest, indent=2) + '\n')
(apk.parent / 'forma.apk.sha256').write_text(f'{sha}  forma.apk\n')
print(f'Validated release manifest for build {code}')
