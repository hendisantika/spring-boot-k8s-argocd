#!/usr/bin/env bash
# Kept for backwards compatibility — tags are now workflow run numbers, not v1/v2.
# Prefer ./build.sh <tag>.
set -euo pipefail

exec "$(dirname "$0")/build.sh" "$@"
