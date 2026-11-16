#!/usr/bin/env bash
set -euo pipefail
# Official upstream scrcpy 3.3.4 server (Apache 2.0). Never trust an unpinned download.
version=3.3.4
sha256=8588238c9a5a00aa542906b6ec7e6d5541d9ffb9b5d0f6e1bc0e365e2303079e
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out="$root/app/src/main/assets/scrcpy-server-v$version"
mkdir -p "$(dirname "$out")"
if [[ -f "$out" ]] && [[ "$(sha256sum "$out" | cut -d' ' -f1)" == "$sha256" ]]; then
  exit 0
fi
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
curl --fail --location --silent --show-error --retry 3 --retry-delay 2 \
  "https://github.com/Genymobile/scrcpy/releases/download/v$version/scrcpy-server-v$version" -o "$tmp"
echo "$sha256  $tmp" | sha256sum -c -
install -m 0644 "$tmp" "$out"
