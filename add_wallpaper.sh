#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export LC_ALL=C

if ! command -v python3 &>/dev/null; then
    echo "Error: python3 is required to run this script." >&2
    exit 1
fi

python3 "$DIR/add_wallpaper.py" "$@"
