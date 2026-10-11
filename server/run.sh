#!/usr/bin/env bash
# Build and run the miru extraction server.
#
# Usage:
#   ./run.sh          # build (release) and run in the foreground
#   ./run.sh debug    # build (debug) and run in the foreground
#
# Environment overrides: MIRU_HOST, MIRU_PORT (default 127.0.0.1:8787).
set -euo pipefail

PROFILE="${1:-release}"

case "$PROFILE" in
    debug)
        cargo build
        BIN="target/debug/miru-server"
        ;;
    release)
        cargo build --release
        BIN="target/release/miru-server"
        ;;
    *)
        echo "unknown profile: $PROFILE (expected 'debug' or 'release')" >&2
        exit 1
        ;;
esac

if ! command -v yt-dlp >/dev/null 2>&1; then
    echo "warning: yt-dlp not found on PATH; extraction will fail" >&2
fi

echo "starting miru-server (${PROFILE}) on ${MIRU_HOST:-127.0.0.1}:${MIRU_PORT:-8787}"
exec "$BIN"