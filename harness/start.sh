#!/usr/bin/env bash
# Starts the BX Lens demo harness: MiniServer with the freshly built module, a Derby in-memory database and the demo app.
#   ./start.sh            build the module, then run in the foreground
#   SKIP_BUILD=1 ./start.sh   reuse build/modules/bx-lens
#   PORT=8085 BOXLANG_VERSION=1.19.0-snapshot ./start.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
BASE_VERSION="$(grep '^boxlangVersion=' "$ROOT/gradle.properties" | cut -d= -f2)"
VERSION="${BOXLANG_VERSION:-${BASE_VERSION}-snapshot}"
PORT="${PORT:-8085}"
CACHE="$HERE/.cache"
RUN="${RUN_DIR:-$HERE/.run}"
DL="https://downloads.ortussolutions.com/ortussolutions"

mkdir -p "$CACHE" "$RUN"
fetch() { [ -s "$CACHE/$2" ] || { echo "+ downloading $2"; curl -fsSL -o "$CACHE/$2" "$1"; }; }
fetch "$DL/boxlang/$VERSION/boxlang-$VERSION.jar" "boxlang-$VERSION.jar"
fetch "$DL/boxlang-runtimes/boxlang-miniserver/$VERSION/boxlang-miniserver-$VERSION.jar" "miniserver-$VERSION.jar"
fetch "$DL/boxlang-runtimes/boxlang-web-support/$VERSION/boxlang-web-support-$VERSION.jar" "web-support-$VERSION.jar"
fetch "$DL/boxlang-modules/bx-derby/1.0.0/bx-derby-1.0.0.zip" "bx-derby.zip"

if [ -z "${SKIP_BUILD:-}" ]; then
	echo "+ building the module"
	(cd "$ROOT" && ./gradlew shadowJar -q --console=plain)
fi

rm -rf "$RUN/home"
mkdir -p "$RUN/home/modules"
cp -R "$HERE/home/." "$RUN/home/"
sed -i "s|@HARNESS_APP@|$HERE/app|g" "$RUN/home/config/boxlang.json"
# LENS_LICENSE=trial|plus|expired|none shows that license state in the console. Empty detects bx-plus.
sed -i "s|@LENS_LICENSE@|${LENS_LICENSE:-}|g" "$RUN/home/config/boxlang.json"
cp -R "$ROOT/build/modules/bx-lens" "$RUN/home/modules/bxLens"
# DEV=1 serves the UI files straight from src/main/bx/assets, so edits show on refresh (needs dev.reloadAssets)
if [ -n "${DEV:-}" ]; then
	rm -rf "$RUN/home/modules/bxLens/assets"
	ln -s "$ROOT/src/main/bx/assets" "$RUN/home/modules/bxLens/assets"
fi
# WITH_AI=1 installs bx-ai and turns on ai.enabled with the provider in LENS_AI_PROVIDER (default ollama, at localhost:11434, see harness/mock-ai.py)
if [ -n "${WITH_AI:-}" ]; then
	fetch "$DL/boxlang-modules/bx-ai/${BX_AI_VERSION:-3.0.0}/bx-ai-${BX_AI_VERSION:-3.0.0}.zip" "bx-ai-${BX_AI_VERSION:-3.0.0}.zip"
	mkdir -p "$RUN/home/modules/bxai"
	unzip -q -o "$CACHE/bx-ai-${BX_AI_VERSION:-3.0.0}.zip" -d "$RUN/home/modules/bxai"
	sed -i "s|\"ai\": { \"enabled\": false|\"ai\": { \"enabled\": true|; s|@LENS_AI_PROVIDER@|${LENS_AI_PROVIDER:-ollama}|" "$RUN/home/config/boxlang.json"
fi
sed -i "s|@LENS_AI_PROVIDER@||" "$RUN/home/config/boxlang.json"
mkdir -p "$RUN/home/modules/derby"
unzip -q -o "$CACHE/bx-derby.zip" -d "$RUN/home/modules/derby"

echo "+ harness on http://localhost:$PORT (BoxLang $VERSION)"
export BOXLANG_HOME="$RUN/home"
exec java -cp "$CACHE/boxlang-$VERSION.jar:$CACHE/miniserver-$VERSION.jar:$CACHE/web-support-$VERSION.jar" \
	ortus.boxlang.web.MiniServer --webroot "$HERE/app" --host 127.0.0.1 --port "$PORT"
