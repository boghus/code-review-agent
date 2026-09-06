#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(mktemp -d)"
trap 'rm -rf "$repo_dir"' EXIT

cd "$repo_dir"
git init -q
git config user.name 'Code Review Agent'
git config user.email 'code-review-agent@example.invalid'

echo 'stable' > kept.txt
git add kept.txt
git commit -qm 'base'
base_sha="$(git rev-parse HEAD)"

echo 'historical change' > historical.txt
git add historical.txt
git commit -qm 'intermediate change'

echo 'final change' >> kept.txt
git add kept.txt
rm historical.txt
git add -u
git commit -qm 'final PR state'
head_sha="$(git rev-parse HEAD)"

diff_path="$repo_dir/pr.diff"
BASE_SHA="$base_sha" HEAD_SHA="$head_sha" DIFF_PATH="$diff_path" bash "$OLDPWD/scripts/build-pr-diff.sh"

if grep -q 'historical change' "$diff_path"; then
  echo 'FAIL: intermediate commit content leaked into the final PR diff' >&2
  exit 1
fi

if grep -q 'historical.txt' "$diff_path"; then
  echo 'FAIL: file present only in an intermediate commit leaked into the final PR diff' >&2
  exit 1
fi

grep -q 'kept.txt' "$diff_path"
grep -q '+final change' "$diff_path"

echo 'PASS: final PR diff contains only changes between base and head'
