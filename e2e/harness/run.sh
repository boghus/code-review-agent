#!/usr/bin/env bash
set -euo pipefail

: "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"
: "${E2E_FIXTURE_REPOSITORY:?E2E_FIXTURE_REPOSITORY is required (owner/repo)}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="${E2E_SCENARIOS_FILE:-$ROOT/scenarios.json}"
SCENARIO="${1:-clean}"
API="https://api.github.com"

api() {
  local method="$1" url="$2" body="${3:-}"
  if [[ -n "$body" ]]; then
    curl --fail-with-body -sS -X "$method" \
      -H "Authorization: Bearer $GITHUB_TOKEN" \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      -H "Content-Type: application/json" "$url" -d "$body"
  else
    curl --fail-with-body -sS -X "$method" \
      -H "Authorization: Bearer $GITHUB_TOKEN" \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" "$url"
  fi
}

cleanup() {
  if [[ "${KEEP_E2E_PR:-false}" == "true" ]]; then
    echo "E2E cleanup skipped: KEEP_E2E_PR=true"
    return
  fi
  if [[ -n "${PR_NUMBER:-}" ]]; then
    api PATCH "$API/repos/$E2E_FIXTURE_REPOSITORY/pulls/$PR_NUMBER" '{"state":"closed"}' >/dev/null || true
  fi
  if [[ -n "${BRANCH:-}" ]]; then
    curl --fail-with-body -sS -X DELETE \
      -H "Authorization: Bearer $GITHUB_TOKEN" \
      -H "Accept: application/vnd.github+json" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      "$API/repos/$E2E_FIXTURE_REPOSITORY/git/refs/heads/$BRANCH" >/dev/null || true
  fi
}
trap cleanup EXIT

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }

scenario="$(jq -e --arg id "$SCENARIO" '.scenarios[] | select(.id == $id)' "$CONFIG")"
[[ "$(jq -r '.enabled // false' <<<"$scenario")" == "true" ]] || {
  echo "Scenario '$SCENARIO' is not enabled" >&2; exit 2;
}

base_ref="$(jq -r '.fixture.baseRef // "main"' <<<"$scenario")"
fixture_path="$(jq -r '.fixture.path' <<<"$scenario")"
fixture_content="$(jq -r '.fixture.content' <<<"$scenario")"
trigger="$(jq -r '.trigger // "opened"' <<<"$scenario")"
expected_verdict="$(jq -r '.expected.verdict // empty' <<<"$scenario")"
marker="$(jq -r '.expected.marker' <<<"$scenario")"
workflow_path="$(jq -r '.workflow.path // empty' <<<"$scenario")"
timeout="$(jq -r '.workflow.timeoutSeconds // 600' <<<"$scenario")"
poll="$(jq -r '.workflow.pollSeconds // 10' <<<"$scenario")"

[[ "$trigger" == "opened" ]] || {
  echo "This baseline harness currently supports trigger=opened only; got $trigger" >&2; exit 2;
}

expected_critical="$(jq -r '.expected.severities.CRITICAL // 0' <<<"$scenario")"
expected_high="$(jq -r '.expected.severities.HIGH // 0' <<<"$scenario")"
expected_medium="$(jq -r '.expected.severities.MEDIUM // 0' <<<"$scenario")"
expected_low="$(jq -r '.expected.severities.LOW // 0' <<<"$scenario")"

base_sha="$(api GET "$API/repos/$E2E_FIXTURE_REPOSITORY/git/ref/heads/$base_ref" | jq -r '.object.sha')"
[[ -n "$base_sha" && "$base_sha" != "null" ]] || { echo "Cannot resolve base ref" >&2; exit 1; }

timestamp="$(date +%s)"
BRANCH="e2e/${SCENARIO}-${timestamp}"
TITLE="test(e2e): ${SCENARIO} fixture ${timestamp}"

echo "Preparing $SCENARIO against $E2E_FIXTURE_REPOSITORY:$base_ref"
api POST "$API/repos/$E2E_FIXTURE_REPOSITORY/git/refs" \
  "$(jq -nc --arg ref "refs/heads/$BRANCH" --arg sha "$base_sha" '{ref:$ref,sha:$sha}')" >/dev/null

content_b64="$(printf '%s' "$fixture_content" | base64 -w 0)"
api PUT "$API/repos/$E2E_FIXTURE_REPOSITORY/contents/$fixture_path" \
  "$(jq -nc --arg message "$TITLE" --arg content "$content_b64" --arg branch "$BRANCH" '{message:$message,content:$content,branch:$branch}')" >/dev/null

pr="$(api POST "$API/repos/$E2E_FIXTURE_REPOSITORY/pulls" \
  "$(jq -nc --arg title "$TITLE" --arg head "$BRANCH" --arg base "$base_ref" '{title:$title,head:$head,base:$base,body:"Automated E2E fixture; managed by the Code Review Agent harness."}')")"
PR_NUMBER="$(jq -r '.number' <<<"$pr")"
PR_URL="$(jq -r '.html_url' <<<"$pr")"
PR_SHA="$(jq -r '.head.sha' <<<"$pr")"
[[ "$PR_NUMBER" != "null" ]] || { echo "Cannot create fixture PR" >&2; exit 1; }
echo "Fixture PR: $PR_URL"

deadline=$(( $(date +%s) + timeout ))
run_status="" run_conclusion="" run_id=""

while (( $(date +%s) < deadline )); do
  branch_q="$(jq -nr --arg branch "$BRANCH" '$branch|@uri')"
  runs="$(api GET "$API/repos/$E2E_FIXTURE_REPOSITORY/actions/runs?event=pull_request&branch=$branch_q&per_page=20")"
  candidate="$(jq -c --arg path "$workflow_path" --arg sha "$PR_SHA" '[.workflow_runs[] | select(.head_sha == $sha and ($path == "" or .path == $path))] | sort_by(.created_at) | last // empty' <<<"$runs")"
  if [[ -n "$candidate" ]]; then
    run_id="$(jq -r '.id' <<<"$candidate")"
    run_status="$(jq -r '.status' <<<"$candidate")"
    run_conclusion="$(jq -r '.conclusion // empty' <<<"$candidate")"
    echo "Workflow $run_id: $run_status ${run_conclusion:-pending}"
    [[ "$run_status" == "completed" ]] && break
  else
    echo "Waiting for consumer workflow..."
  fi
  sleep "$poll"
done

[[ "$run_status" == "completed" ]] || { echo "FAIL: workflow timeout" >&2; exit 1; }
[[ "$run_conclusion" == "success" ]] || { echo "FAIL: workflow conclusion=$run_conclusion" >&2; exit 1; }

comments="$(api GET "$API/repos/$E2E_FIXTURE_REPOSITORY/issues/$PR_NUMBER/comments?per_page=100")"
review="$(jq -r --arg marker "$marker" '[.[] | select(.user.login == "github-actions[bot]" and (.body | contains($marker)))] | last | .body // empty' <<<"$comments")"
[[ -n "$review" ]] || { echo "FAIL: CRA comment not found" >&2; exit 1; }

for pair in "CRITICAL:$expected_critical" "HIGH:$expected_high" "MEDIUM:$expected_medium" "LOW:$expected_low"; do
  severity="${pair%%:*}"; expected="${pair#*:}"
  printf '%s' "$review" | grep -q "$expected $severity" || {
    echo "FAIL: expected $expected $severity" >&2; exit 1;
  }
done

printf '%s\n' "$review" | grep -q -- "$marker" || { echo "FAIL: marker assertion" >&2; exit 1; }
if [[ -n "$expected_verdict" ]]; then
  printf '%s' "$review" | grep -q "Verdict: $expected_verdict" || {
    echo "FAIL: expected verdict $expected_verdict" >&2; exit 1;
  }
fi

echo "PASS: $SCENARIO (PR #$PR_NUMBER, workflow $run_id)"
