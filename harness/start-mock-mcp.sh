#!/usr/bin/env bash
# Starts the mock MCP servers (harness/mock-mcp, written in BoxLang) on MiniServer: http://127.0.0.1:11435/mcp.bxs/{server}
#   ./start-mock-mcp.sh                 foreground
#   MOCK_MCP_PORT=11435 ./start-mock-mcp.sh
# Needs the jars that start.sh downloads into harness/.cache (run start.sh once, or Playwright, first).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$HERE")"
BASE_VERSION="$(grep '^boxlangVersion=' "$ROOT/gradle.properties" | cut -d= -f2)"
VERSION="${BOXLANG_VERSION:-${BASE_VERSION}-snapshot}"
PORT="${MOCK_MCP_PORT:-11435}"
CACHE="$HERE/.cache"
RUN="${RUN_DIR:-$HERE/.run-mcp}"
DL="https://downloads.ortussolutions.com/ortussolutions"

mkdir -p "$CACHE" "$RUN/home"
fetch() { [ -s "$CACHE/$2" ] || { echo "+ downloading $2"; curl -fsSL -o "$CACHE/$2" "$1"; }; }
fetch "$DL/boxlang/$VERSION/boxlang-$VERSION.jar" "boxlang-$VERSION.jar"
fetch "$DL/boxlang-runtimes/boxlang-miniserver/$VERSION/boxlang-miniserver-$VERSION.jar" "miniserver-$VERSION.jar"
fetch "$DL/boxlang-runtimes/boxlang-web-support/$VERSION/boxlang-web-support-$VERSION.jar" "web-support-$VERSION.jar"

echo "+ mock MCP servers on http://127.0.0.1:$PORT/mcp.bxs/{server} (BoxLang $VERSION)"
export BOXLANG_HOME="$RUN/home"
exec java -cp "$CACHE/boxlang-$VERSION.jar:$CACHE/miniserver-$VERSION.jar:$CACHE/web-support-$VERSION.jar" \
	ortus.boxlang.web.MiniServer --webroot "$HERE/mock-mcp" --host 127.0.0.1 --port "$PORT"
