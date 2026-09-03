#!/usr/bin/env bash
set -euo pipefail

ACTION_FILE="${1:-action.yml}"

# Extract only the composite step named "Run Code Review Agent".
# Sibling steps are indented at four spaces in action.yml, while nested
# properties use deeper indentation.
run_step=$(awk '
  /^    - name: Run Code Review Agent$/ { flag=1; next }
  flag && /^    - name: / { exit }
  flag { print }
' "$ACTION_FILE")

# The AI/Gradle step must explicitly clear the GitHub token. Allow harmless
# YAML formatting differences while requiring an actually empty value.
grep -qE "^[[:space:]]*GITHUB_TOKEN:[[:space:]]*(''|\"\")$" <<<"$run_step" || {
  echo "Security check failed: Run Code Review Agent must clear GITHUB_TOKEN." >&2
  exit 1
}

# The GitHub token input may only be consumed by the GitHub comment steps.
if grep -qE 'CRA_.*GITHUB|CRA_GITHUB_TOKEN|GITHUB_TOKEN:.*inputs\.github-token' <<<"$run_step"; then
  echo "Security check failed: GitHub token is exposed to the AI process." >&2
  exit 1
fi

# Keep GitHub API permissions least-privileged in the documented consumer example.
permissions_block=$(awk '/^permissions:/{flag=1; next} flag && /^[^ ]/{exit} flag{print}' README.md)
grep -qE '^  contents: read$' <<<"$permissions_block" || {
  echo "Security check failed: README must document contents: read." >&2
  exit 1
}
grep -qE '^  pull-requests: write$' <<<"$permissions_block" || {
  echo "Security check failed: README must document pull-requests: write." >&2
  exit 1
}

printf '%s\n' "Security check passed: GitHub token is isolated from the AI process and permissions are documented."
