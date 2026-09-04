#!/usr/bin/env bash
set -euo pipefail

RULES_RELATIVE="${RULES_RELATIVE:?RULES_RELATIVE is required}"
BASE_SHA="${BASE_SHA:?BASE_SHA is required}"
GITHUB_OUTPUT="${GITHUB_OUTPUT:?GITHUB_OUTPUT is required}"

# rules-path is a Git tree path, not a filesystem path. Keep the accepted
# contract deliberately narrow so the value can never become shell syntax,
# an absolute filesystem path, or a parent traversal path.
if [[ -z "$RULES_RELATIVE" || "$RULES_RELATIVE" == /* || "$RULES_RELATIVE" == -* ]]; then
  echo "::error::rules-path must be a non-empty repository-relative path and must not start with '-': '${RULES_RELATIVE}'" >&2
  exit 1
fi

IFS='/' read -r -a components <<< "$RULES_RELATIVE"
for component in "${components[@]}"; do
  if [[ -z "$component" || "$component" == "." || "$component" == ".." ]]; then
    echo "::error::rules-path contains an unsafe path component: '${RULES_RELATIVE}'" >&2
    exit 1
  fi
done

# Verify the exact base-tree entry is a regular blob. We intentionally read
# only from BASE_SHA so PR-controlled workspace content cannot become trusted
# review instructions.
ENTRY_TYPE=$(git ls-tree "$BASE_SHA" -- "$RULES_RELATIVE" | awk -v path="$RULES_RELATIVE" '$4 == path { print $2; exit }')
if [[ -z "$ENTRY_TYPE" ]]; then
  echo "::error::No entry '${RULES_RELATIVE}' in base ref ${BASE_SHA}. rules-path must point to an existing file." >&2
  exit 1
fi

if [[ "$ENTRY_TYPE" != "blob" ]]; then
  echo "::error::rules-path '${RULES_RELATIVE}' is a ${ENTRY_TYPE} in base ref ${BASE_SHA}, not a regular file. Symlinks and submodules are not allowed." >&2
  exit 1
fi

RULES_FILE="$(mktemp -p "${RUNNER_TEMP:-/tmp}" cra-rules-XXXXXX.md)"
cleanup() {
  rm -f "$RULES_FILE"
}
trap cleanup EXIT
chmod 0644 "$RULES_FILE"

if ! git show "${BASE_SHA}:${RULES_RELATIVE}" > "$RULES_FILE"; then
  echo "::error::git show failed for ${BASE_SHA}:${RULES_RELATIVE}" >&2
  exit 1
fi

echo "rules-path=${RULES_FILE}" >> "$GITHUB_OUTPUT"
echo "Loaded trusted rules from ${BASE_SHA}:${RULES_RELATIVE} (${ENTRY_TYPE}) → ${RULES_FILE}"
