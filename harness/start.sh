#!/usr/bin/env bash
# Starts the BX Lens demo harness: MiniServer with the freshly built module, a Derby in-memory database and the demo app.
#   ./start.sh            build the module, then run in the foreground
#   SKIP_BUILD=1 ./start.sh   reuse build/modules/bx-lens
#   PORT=8085 BOXLANG_VERSION=1.19.0-snapshot ./start.sh
#   WITH_ORM=1 [BX_ORM_VERSION=1.7.2-snapshot | BX_ORM_DIR=~/bx-orm] ./start.sh
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
# LENS_RELOAD_ASSETS=false serves the UI files like production: hashed URLs, kept by the browser for a year
sed -i "s|\"reloadAssets\": true|\"reloadAssets\": ${LENS_RELOAD_ASSETS:-true}|" "$RUN/home/config/boxlang.json"
# LENS_LICENSE=trial|plus|expired|none shows that license state in the console. Empty detects bx-plus.
sed -i "s|@LENS_LICENSE@|${LENS_LICENSE:-}|g" "$RUN/home/config/boxlang.json"
# LENS_SERVER_HEADER=true also sends X-BxLens-Server. LENS_SERVER_NAME and LENS_SERVER_ADDRESS (environment) set the server identity
sed -i "s|@LENS_SERVER_HEADER@|${LENS_SERVER_HEADER:-false}|g" "$RUN/home/config/boxlang.json"
# LENS_READONLY=true starts the console read only. LENS_SEED_OVERRIDES=/path/file.json is copied in as the saved settings (config/bxlens-settings.json),
# which is how a read only console gets MCP servers switched on.
sed -i "s|@LENS_READONLY@|${LENS_READONLY:-false}|g" "$RUN/home/config/boxlang.json"
if [ -n "${LENS_SEED_OVERRIDES:-}" ]; then
	cp "$LENS_SEED_OVERRIDES" "$RUN/home/config/bxlens-settings.json"
fi
# LENS_MCP_BASE=http://127.0.0.1:11435/mcp.bxs serves the builtin MCP documentation servers from the mock (harness/start-mock-mcp.sh) at base/id,
# so nothing needs the internet. Empty uses the real servers.
sed -i "s|@LENS_MCP_BASE@|${LENS_MCP_BASE:-}|g" "$RUN/home/config/boxlang.json"
cp -R "$ROOT/build/modules/bx-lens" "$RUN/home/modules/bxLens"
# DEV=1 serves the UI files straight from src/main/bx/assets, so edits show on refresh (needs dev.reloadAssets)
if [ -n "${DEV:-}" ]; then
	rm -rf "$RUN/home/modules/bxLens/assets"
	ln -s "$ROOT/src/main/bx/assets" "$RUN/home/modules/bxLens/assets"
fi
# bx-ai ships inside the module (modules/bxai). WITH_AI=1 turns on ai.enabled with the provider in LENS_AI_PROVIDER (default ollama, at
# localhost:11434, see harness/mock-ai.py)
if [ -n "${WITH_AI:-}" ]; then
	sed -i "s|\"ai\": { \"enabled\": false|\"ai\": { \"enabled\": true|; s|@LENS_AI_PROVIDER@|${LENS_AI_PROVIDER:-ollama}|" "$RUN/home/config/boxlang.json"
fi
sed -i "s|@LENS_AI_PROVIDER@||" "$RUN/home/config/boxlang.json"
# WITH_ORM=1 installs bx-orm and turns the ORM entity in the demo app on. Lens listens to its onORMQuery, onORMFlush and onORMException events,
# which exist from bx-orm 1.7.2 (BX_ORM_VERSION, default 1.7.2-snapshot, the build published on ForgeBox and downloads.ortussolutions.com).
# BX_ORM_DIR=/path/to/bx-orm builds the module from a checkout instead (./gradlew build) and unzips build/distributions/*.zip.
# A snapshot is a moving target: delete harness/.cache/bx-orm-*-snapshot.zip to download it again.
if [ -n "${WITH_ORM:-}" ]; then
	mkdir -p "$RUN/home/modules/bxorm"
	if [ -n "${BX_ORM_DIR:-}" ]; then
		echo "+ building bx-orm from $BX_ORM_DIR"
		(cd "$BX_ORM_DIR" && ./gradlew build -x test -q --console=plain)
		ORM_ZIP="$(ls -t "$BX_ORM_DIR"/build/distributions/*.zip | head -n 1)"
	else
		ORM_VERSION="${BX_ORM_VERSION:-1.7.2-snapshot}"
		fetch "$DL/boxlang-modules/bx-orm/$ORM_VERSION/bx-orm-$ORM_VERSION.zip" "bx-orm-$ORM_VERSION.zip"
		ORM_ZIP="$CACHE/bx-orm-$ORM_VERSION.zip"
	fi
	unzip -q -o "$ORM_ZIP" -d "$RUN/home/modules/bxorm"
	export LENS_ORM=1
fi
mkdir -p "$RUN/home/modules/derby"
unzip -q -o "$CACHE/bx-derby.zip" -d "$RUN/home/modules/derby"

echo "+ harness on http://localhost:$PORT (BoxLang $VERSION)"
export BOXLANG_HOME="$RUN/home"
# Lets /~bxlens/ reach BoxLang on MiniServer: the default pass predicate plus the console path (see docs/guides/production.md)
export BOXLANG_PASS_PREDICATE='regex( '"'"'^(/.+?\.cfml|/.+?\.cf[cms]|.+?\.bx[ms]{0,1})(/.*)?$'"'"' ) or regex( '"'"'^/~bxlens/?$'"'"' )'
exec java -cp "$CACHE/boxlang-$VERSION.jar:$CACHE/miniserver-$VERSION.jar:$CACHE/web-support-$VERSION.jar" \
	ortus.boxlang.web.MiniServer --webroot "$HERE/app" --host 127.0.0.1 --port "$PORT"
