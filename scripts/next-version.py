#!/usr/bin/env python3
"""Choose a monotonically increasing Android version, even after workflow reruns."""
import json
import os
import subprocess
import urllib.request
from urllib.parse import urlsplit

repo = os.environ['GITHUB_REPOSITORY']
result = subprocess.run(['gh', 'api', f'repos/{repo}/releases/latest'], capture_output=True, text=True)
previous = 0
if result.returncode:
    if 'HTTP 404' not in result.stderr:
        raise SystemExit('Cannot determine the latest published version; refusing to reset the version counter.')
else:
    release = json.loads(result.stdout)
    metadata = [a for a in release['assets'] if a['name'] == 'update.json']
    if len(metadata) != 1:
        raise SystemExit('The latest release has no unique update.json version manifest.')
    url = metadata[0]['browser_download_url']
    parsed = urlsplit(url)
    if parsed.scheme != 'https' or parsed.hostname != 'github.com' or not parsed.path.startswith(f'/{repo}/releases/download/'):
        raise SystemExit('Unexpected metadata download location.')
    with urllib.request.urlopen(url, timeout=30) as response:
        data = response.read(65537)
    if len(data) > 65536:
        raise SystemExit('The previous update manifest is too large.')
    previous = json.loads(data)['versionCode']
    if type(previous) is not int or previous < 1:
        raise SystemExit('The previous Android version code is invalid.')
code = max(3, previous) + 1
if code > 2100000000:
    raise SystemExit('Android version-code limit reached.')
with open(os.environ['GITHUB_OUTPUT'], 'a') as output:
    output.write(f'code={code}\nname=1.4.0\ntag=build-{code}\n')
print(f'Building Forma 1.4.0, Android build {code}')
