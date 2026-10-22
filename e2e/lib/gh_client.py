"""GitHub access for the E2E suite, built on the ``gh`` CLI.

``gh`` is used rather than raw ``curl`` because it already owns
authentication, pagination and TLS. ``jq`` is deliberately not required: it is
absent from some environments, so JSON parsing happens here with ``json``.
"""

import json
import subprocess
import time

from assertions import MARKER

BOT_LOGIN = "github-actions[bot]"


class GhError(RuntimeError):
    pass


def _run(args, check=True):
    process = subprocess.Popen(
        args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
    )
    stdout, stderr = process.communicate()
    if check and process.returncode != 0:
        raise GhError("%s failed (%d): %s" % (" ".join(args[:2]), process.returncode,
                                               stderr.strip()))
    return stdout, stderr, process.returncode


def gh(args, check=True):
    """Run a gh subcommand and return stdout."""
    stdout, _, _ = _run(["gh"] + args, check=check)
    return stdout


def gh_json(args):
    return json.loads(gh(args))


def api_json(path):
    return gh_json(["api", path])


def comments_for_pr(repo, number):
    """Return every issue comment on a pull request, oldest first."""
    comments = []
    for comment in api_json("repos/%s/issues/%d/comments?per_page=100" % (repo, number)):
        comments.append(comment)
    return comments


def cra_comments(repo, number):
    """Comments posted by the action, identified by author and marker.

    The marker is part of the production contract: ``action.yml`` uses it to
    find and update the existing comment instead of creating a second one, so
    anything carrying it is the action's output.
    """
    return [
        comment
        for comment in comments_for_pr(repo, number)
        if comment.get("user", {}).get("login") == BOT_LOGIN and MARKER in (comment.get("body") or "")
    ]


def open_pr_for_branch(repo, head_branch, base_branch):
    """Return the open pull request whose head is ``head_branch``, or None."""
    result = gh_json([
        "pr", "list", "--repo", repo, "--head", head_branch,
        "--state", "open", "--json", "number,headRefName,baseRefName",
    ])
    for pr in result:
        if pr.get("headRefName") == head_branch and pr.get("baseRefName") == base_branch:
            return pr
    return None


def create_pr(repo, head_branch, base_branch, title, body):
    number = gh([
        "pr", "create", "--repo", repo, "--base", base_branch,
        "--head", head_branch, "--title", title, "--body", body,
    ])
    for line in number.splitlines():
        line = line.strip()
        if line.isdigit():
            return int(line)
    prs = gh_json(["pr", "list", "--repo", repo, "--head", head_branch,
                   "--state", "open", "--json", "number"])
    return prs[0]["number"] if prs else None


def ensure_pr(repo, scenario, base_branch):
    """Reuse the scenario's pull request, creating it when absent.

    Reuse matters: re-running the suite against a different ref must not
    scatter new branches and pull requests across the repository.
    """
    existing = open_pr_for_branch(repo, scenario.branch, base_branch)
    if existing:
        return existing["number"], False
    body = (
        "Automated E2E fixture for the Code Review Agent.\n\n"
        "Scenario: `%s` -- %s\n\n"
        "This pull request must not be merged. It exists to exercise the "
        "action end to end and is kept open by `qa-merge-guard`."
        % (scenario.name, scenario.title)
    )
    number = create_pr(repo, scenario.branch, base_branch,
                       "[e2e] %s: %s" % (scenario.name, scenario.title), body)
    return number, True


def push_empty_commit(repo, branch, message):
    """Push an empty commit to trigger ``pull_request.synchronize``.

    The idempotency scenario needs a second run of the action against the same
    pull request. An empty commit produces a real new head SHA without
    changing the reviewed diff, so the second review is comparable.
    """
    _run(["git", "fetch", "origin", branch])
    _run(["git", "checkout", "-B", branch, "origin/%s" % branch])
    _run(["git", "commit", "--allow-empty", "-m", message])
    _run(["git", "push", "origin", "HEAD:%s" % branch])


def head_sha(repo, branch):
    """Current head commit of a branch."""
    result = gh_json(["api", "repos/%s/branches/%s" % (repo, branch)])
    return result["commit"]["sha"]


def workflow_runs_for_sha(repo, sha):
    """Recent workflow runs for a commit, newest first."""
    return gh_json([
        "run", "list", "--repo", repo, "--commit", sha,
        "--limit", "20",
        "--json", "databaseId,name,status,conclusion,headSha,event,createdAt,startedAt",
    ])


def wait_for_ci_run(repo, sha, timeout=900, log=None, poll=15):
    """Wait until the CI run for a commit finishes, then return it.

    Waiting only for a comment is not enough: a pull request can carry a stale
    comment from the previous run, so the harness would read the old result and
    report it as the current one. This blocks on the run for the exact head
    commit that is under test.

    Returns ``(run, elapsed_seconds)`` where ``run`` is the Examples CI run, or
    None when the timeout elapses with no run started.
    """
    workflow_name = "Examples CI"
    deadline = time.time() + timeout
    started = time.time()
    seen = None

    while True:
        runs = workflow_runs_for_sha(repo, sha)
        target = None
        for run in runs:
            if run.get("name") == workflow_name:
                target = run
                break
        seen = target or seen

        if target is not None:
            if target.get("status") == "completed":
                return target, time.time() - started
        elif time.time() >= deadline:
            return None, time.time() - started

        if time.time() >= deadline:
            return seen, time.time() - started

        if log:
            state = (target or {}).get("status", "not started")
            log("      Examples CI %s (%ds elapsed)" % (state, int(time.time() - started)))
        time.sleep(poll)


def wait_for_review(repo, number, timeout=480, initial_delay=5, max_delay=30,
                    log=None, not_before=None):
    """Poll until the action publishes exactly one CRA comment.

    ``not_before`` is an ISO-8601 timestamp. When given, a comment published
    before it is ignored. A pull request keeps the comment from its previous
    run, so without this bound the harness would grade a stale result as the
    current one, which is exactly the failure this guards against.

    Returns ``(comment, elapsed_seconds)``. Raises ``TimeoutError`` when no
    fresh comment appears within ``timeout`` seconds.
    """
    deadline = time.time() + timeout
    delay = initial_delay
    started = time.time()
    while True:
        found = [comment for comment in cra_comments(repo, number)
                 if not_before is None
                 or (comment.get("updated_at") or "") >= not_before]
        if found:
            return found[0], time.time() - started
        if time.time() >= deadline:
            raise TimeoutError(
                "no Code Review Agent comment on #%d after %ds%s"
                % (number, timeout,
                   "" if not_before is None else " published at or after %s" % not_before)
            )
        if log:
            log("      waiting for the comment (%ds elapsed)" % int(time.time() - started))
        time.sleep(delay)
        delay = min(delay + 5, max_delay)


def wait_for_update(repo, number, previous_comment_id, previous_updated_at,
                    timeout=480, log=None):
    """Wait until the CRA comment is updated in place.

    The contract requires the comment ID to stay the same, so waiting for an ID
    change would be the wrong signal. This waits for ``updated_at`` to advance
    past ``previous_updated_at`` while the ID holds, which is exactly what
    ``create-or-update-comment`` produces.

    Returns ``(comment, elapsed_seconds)``. A second comment ID is a contract
    violation and raises ``GhError`` immediately rather than on timeout.
    """
    deadline = time.time() + timeout
    started = time.time()
    while True:
        found = cra_comments(repo, number)

        for comment in found:
            if comment["id"] != previous_comment_id:
                raise GhError(
                    "the action created a second comment (%d) instead of updating %d"
                    % (comment["id"], previous_comment_id)
                )

        if not found:
            raise GhError("the Code Review Agent comment %d disappeared" % previous_comment_id)

        current = found[0].get("updated_at") or ""
        if current > previous_updated_at:
            return found[0], time.time() - started

        if time.time() >= deadline:
            raise TimeoutError(
                "comment %d was not updated within %ds" % (previous_comment_id, timeout)
            )
        if log:
            log("      waiting for the update (%ds elapsed)" % int(time.time() - started))
        time.sleep(10)