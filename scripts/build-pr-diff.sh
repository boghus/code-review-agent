#!/usr/bin/env bash
set -euo pipefail

: "${BASE_SHA:?BASE_SHA is required}"
: "${HEAD_SHA:?HEAD_SHA is required}"
: "${DIFF_PATH:?DIFF_PATH is required}"

git diff --no-ext-diff --unified=80 "$BASE_SHA" "$HEAD_SHA" > "$DIFF_PATH"
