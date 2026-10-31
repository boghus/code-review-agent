"""GitHub access for the E2E suite, built on the ``gh`` CLI.

``gh`` is used rather than raw ``curl`` because it already owns
authentication, pagination and TLS. ``jq`` is deliberately not required: it is
absent from some environments, so JSON parsing happens here with ``json``.
"""

import base64
import json
import os
import subprocess
import time

from assertions import MARKER

BOT_LOGIN = "github-actions[bot]"


class GhError(RuntimeError):
    pass


GH_CALL_TIMEOUT = 120


def _run(args, check=True, timeout=GH_CALL_TIMEOUT):
    """Run a command, never blocking indefinitely.

    ``gh`` can block for a long time when it decides to prompt, for example
    when the token expired and it wants to open a browser. Without a timeout
    a single call can stall the suite for hours, so every call is bounded.
    """
    process = subprocess.Popen(
        args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        universal_newlines=True,
        stdin=subprocess.DEVNULL,
        env=_gh_env(),
    )
    try:
        stdout, stderr = process.communicate(timeout=timeout)
    except subprocess.TimeoutExpired:
        process.kill()
        process.communicate()
        raise GhError("%s timed out after %ds" % (" ".join(args[:2]), timeout))
    if check and process.returncode != 0:
        raise GhError("%s failed (%d): %s" % (" ".join(args[:2]), process.returncode,
                                               stderr.strip()))
    return stdout, stderr, process.returncode


def _gh_env():
    """Environment that keeps gh from trying to interact with the user."""
    env = dict(os.environ)
    # gh asks for confirmation before destructive calls such as deleting a
    # ref. The harness only deletes refs it created, but answering yes
    # automatically keeps it from waiting on stdin.
    env.setdefault("GH_PROMPT_DISABLED", "1")
    env.setdefault("GH_NO_UPDATE_NOTIFIER", "1")
    return env


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


def ensure_pr(repo, scenario, base_branch, fixture_content=None, fixture_path=None,
              log=None):
    """Reuse the scenario's pull request, creating the branch and PR when absent.

    Reuse matters: re-running the suite must not scatter new branches and pull
    requests across the repository. When the branch is missing it is recreated
    from ``base_branch`` with the fixture file applied, so the suite works even
    after the fixture pull requests were closed.

    Returns ``(number, created)``.
    """
    existing = open_pr_for_branch(repo, scenario.branch, base_branch)
    if existing:
        return existing["number"], False

    if fixture_content is not None and fixture_path is not None:
        apply_fixture_commit(
            repo, scenario.branch, base_branch, fixture_path, fixture_content,
            "[e2e] create %s fixture" % scenario.name,
        )
        if log:
            log("  %s: fixture branch recreated from %s" % (scenario.name, base_branch))
    else:
        create_branch_from(repo, scenario.branch, base_branch)

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
    """Push an empty commit to a branch through the GitHub API.

    Used to trigger ``pull_request.synchronize`` and to start a run when the
    branch has no CI run yet. The commit is created from the API rather than
    from the local checkout so the harness never switches branches or alters
    the working tree, which matters because scenarios run in parallel and
    share one local repository.

    Returns the new head SHA.
    """
    head = head_sha(repo, branch)
    # The tree parameter takes a tree SHA, not a commit SHA, so read the tree
    # of the current head and reuse it for the empty commit.
    tree = gh_json(["api", "repos/%s/git/commits/%s" % (repo, head)])["tree"]["sha"]
    commit = gh_json([
        "api", "--method", "POST",
        "repos/%s/git/commits" % repo,
        "-f", "message=%s" % message,
        "-f", "tree=%s" % tree,
        "-f", "parents[]=%s" % head,
    ])
    new_sha = commit["sha"]
    gh_json([
        "api", "--method", "PATCH",
        "repos/%s/git/refs/heads/%s" % (repo, branch),
        "-f", "sha=%s" % new_sha,
    ])
    return new_sha


def head_sha(repo, branch):
    """Current head commit of a branch."""
    result = gh_json(["api", "repos/%s/branches/%s" % (repo, branch)])
    return result["commit"]["sha"]


def branch_exists(repo, branch):
    """True when the branch exists in the repository."""
    _, _, code = _run(
        ["git", "ls-remote", "--exit-code", "--heads",
         "https://github.com/%s.git" % repo, branch],
        check=False,
    )
    return code == 0


def reset_branch_to(repo, branch, source_ref):
    """Point a branch at ``source_ref``, discarding whatever it held.

    The branch has to be reset, not left in place: the workflow on that branch
    runs the action from its own HEAD, so a branch that still pointed at older
    code would validate the old code and report a pass for it.
    """
    source = resolve_sha(repo, source_ref)
    if branch_exists(repo, branch):
        current = head_sha(repo, branch)
        if current == source:
            return False
        gh_json([
            "api", "--method", "PATCH",
            "repos/%s/git/refs/heads/%s" % (repo, branch),
            "-f", "sha=%s" % source,
            "-f", "force=true",
        ])
        return True
    gh_json([
        "api", "--method", "POST",
        "repos/%s/git/refs" % repo,
        "-f", "ref=refs/heads/%s" % branch,
        "-f", "sha=%s" % source,
    ])
    return True


def create_branch_from(repo, branch, source_ref):
    """Create a branch pointing at ``source_ref`` when it does not exist yet.

    The fixture branches carry the change under test, so they are created from
    the reference being validated rather than from the default branch. That is
    what makes a run against a tag or a release candidate meaningful.
    """
    if branch_exists(repo, branch):
        return False
    source = resolve_sha(repo, source_ref)
    gh_json([
        "api", "--method", "POST",
        "repos/%s/git/refs" % repo,
        "-f", "ref=refs/heads/%s" % branch,
        "-f", "sha=%s" % source,
    ])
    return True


def read_file_at_ref(repo, ref, path):
    """Read a file as it exists at a given ref, or None when absent."""
    stdout, _, code = _run(
        ["gh", "api", "repos/%s/contents/%s?ref=%s" % (repo, path, ref)],
        check=False,
    )
    if code != 0:
        return None
    try:
        payload = json.loads(stdout)
    except ValueError:
        return None
    if not isinstance(payload, dict) or "content" not in payload:
        return None
    return base64.b64decode(payload["content"]).decode("utf-8")


def resolve_sha(repo, ref):
    """Resolve a branch, tag or SHA to a full commit SHA.

    The git database API only accepts full SHAs, so a branch or a tag name has
    to be resolved first. A tag is read through the git refs endpoint, which
    takes a tag name, and an annotated tag is dereferenced to the commit it
    points at.
    """
    if len(ref) == 40 and all(c in "0123456789abcdef" for c in ref.lower()):
        return ref

    try:
        return head_sha(repo, ref)
    except (GhError, KeyError, ValueError):
        pass

    # git/ref/tags/<name> resolves a tag name to its object. An annotated tag
    # points at a tag object rather than at a commit, so it is dereferenced.
    result = gh_json(["api", "repos/%s/git/ref/tags/%s" % (repo, ref)])
    target = result.get("object") or {}
    if target.get("type") == "tag":
        tag = gh_json(["api", "repos/%s/git/tags/%s" % (repo, target["sha"])])
        return tag["object"]["sha"]
    return target["sha"]


def commit_tree_sha(repo, sha):
    """Tree SHA of a commit."""
    return gh_json(["api", "repos/%s/git/commits/%s" % (repo, sha)])["tree"]["sha"]


def apply_fixture_commit(repo, branch, source_ref, fixture_path, content,
                          message):
    """Apply the fixture change to a branch and push it.

    The branch is reset to ``source_ref`` and then receives the fixture file,
    so the diff the reviewer sees is exactly the fixture change and nothing
    else, and the code the reviewer executes is the code of ``source_ref``.
    """
    reset_branch_to(repo, branch, source_ref)

    head = head_sha(repo, branch)
    base_tree = commit_tree_sha(repo, head)
    tree = gh_json([
        "api", "--method", "POST",
        "repos/%s/git/trees" % repo,
        "-f", "base_tree=%s" % base_tree,
        "-f", "tree[][path]=%s" % fixture_path,
        "-f", "tree[][mode]=100644",
        "-f", "tree[][type]=blob",
        # The trees endpoint takes the content as plain text. Only the
        # contents endpoint takes it base64 encoded.
        "-f", "tree[][content]=%s" % content,
    ])
    commit = gh_json([
        "api", "--method", "POST",
        "repos/%s/git/commits" % repo,
        "-f", "message=%s" % message,
        "-f", "tree=%s" % tree["sha"],
        "-f", "parents[]=%s" % head,
    ])
    gh_json([
        "api", "--method", "PATCH",
        "repos/%s/git/refs/heads/%s" % (repo, branch),
        "-f", "sha=%s" % commit["sha"],
    ])
    return commit["sha"]


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
            # Match on the name and on the commit. The list is filtered by
            # commit already, but the branch also triggers the workflow on
            # push, and both runs can appear for the same commit.
            if run.get("name") == workflow_name and run.get("headSha") == sha:
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