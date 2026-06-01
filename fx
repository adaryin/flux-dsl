#!/usr/bin/env bash
set -eu
script_dir="$(cd "$(dirname "$0")" && pwd)"
PYTHONPATH="$script_dir/parsers/python" exec python3 -m fluxdsl.cli "$@"
