#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOADER="$SCRIPT_DIR/load-trusted-rules.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

REPO="$TMP_DIR/repo"
mkdir -p "$REPO/.github"
git -C "$REPO" init -q
git -C "$REPO" config user.email "security-test@example.invalid"
git -C "$REPO" config user.name "Security Test"
printf '%s\n' '# trusted rules' > "$REPO/.github/code_review_rules.md"
git -C "$REPO" add .
git -C "$REPO" commit -q -m 'test fixture'
BASE_SHA="$(git -C "$REPO" rev-parse HEAD)"

run_loader() {
  local payload="$1"
  local output_file="$TMP_DIR/output"
  : > "$output_file"

  (
    cd "$REPO"
    RULES_RELATIVE="$payload" \
    BASE_SHA="$BASE_SHA" \
    GITHUB_OUTPUT="$output_file" \
    RUNNER_TEMP="$TMP_DIR" \
    bash "$LOADER"
  )
}

# A normal repository-relative path must still work and must be loaded from
# the base tree rather than from the PR workspace.
run_loader '.github/code_review_rules.md'
RULES_FILE="$(sed -n 's/^rules-path=//p' "$TMP_DIR/output")"
test -n "$RULES_FILE"
test "$(cat "$RULES_FILE")" = '# trusted rules'

MARKER="$TMP_DIR/pwned"
BACKTICK_PAYLOAD='`touch "'"$MARKER"'"`'

assert_rejected_or_inert() {
  local payload="$1"
  : > "$TMP_DIR/output"
  rm -f "$MARKER"

  set +e
  run_loader "$payload"
  status=$?
  set -e

  # The important security property is that the payload is data, never shell
  # source: command substitutions/backticks must not execute and traversal
  # inputs must not escape the repository tree.
  if [[ -e "$MARKER" ]]; then
    echo "Security check failed: payload executed: $payload" >&2
    exit 1
  fi

  test "$status" -ne 0
}

assert_rejected_or_inert '../etc/passwd'
assert_rejected_or_inert 'foo/../../bar'
assert_rejected_or_inert '/etc/passwd'
assert_rejected_or_inert "\$(touch \"$MARKER\")"
assert_rejected_or_inert "\$(echo malicious)"
assert_rejected_or_inert "$BACKTICK_PAYLOAD"
assert_rejected_or_inert '`echo malicious`'

printf '%s\n' 'Security check passed: rules-path is passed as data, traversal is rejected, and command substitution/backticks remain inert.'
