"""Scenario runner: execute one scenario and produce a Result."""

import os
import subprocess
import time
import traceback

import assertions
import gh_client
import scenarios as scenario_registry

PROVIDER_FAILURE_CLASS = "provider"
# A provider outage covers both an exhausted quota and a saturated model
# answering 503. Both are transient, and the provider itself says to retry
# later, so the wait between attempts is generous.
PROVIDER_RETRY_DELAY = 180


def _provider_message(body):
    """First sentence of the provider failure comment, for the report."""
    lines = [line.strip() for line in body.splitlines()]
    for line in lines:
        if line and not line.startswith(("#", "<!--", ">", "---")):
            return line[:160]
    return ""


class Result(object):
    def __init__(self, scenario, status, checks=None, pr_number=None,
                 comment_id=None, failure_class="", error="", elapsed=0.0,
                 body=None, artifact=None):
        self.scenario = scenario
        self.status = status
        self.checks = checks or []
        self.pr_number = pr_number
        self.comment_id = comment_id
        self.failure_class = failure_class
        self.error = error
        self.elapsed = elapsed
        self.body = body
        self.artifact = artifact

    @property
    def passed(self):
        return self.status == "PASS"

    @property
    def failed_checks(self):
        return [check for check in self.checks if not check.ok]


def _write_artifact(base_dir, scenario, body, ref):
    """Persist the published comment so a FAIL can be diagnosed later.

    Without a retry, the artifact is the only evidence separating a product
    bug from model variance, so it is written for every non-PASS result.
    """
    if not base_dir or body is None:
        return None
    target_dir = os.path.join(base_dir, ref)
    os.makedirs(target_dir, exist_ok=True)
    path = os.path.join(target_dir, "%s.md" % scenario.name)
    with open(path, "w") as handle:
        handle.write(body)
    return path


FIXTURE_FILES = {
    "clean": "clean/CleanFixture.groovy",
    "critical": "critical/ConfigFixture.groovy",
    "high": "high/CounterFixture.groovy",
    "medium": "medium/FormatterFixture.groovy",
    "idempotency": "idempotency/IdempotencyFixture.groovy",
}

FIXTURES_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                            "fixtures")


def _fixture_content(scenario, repo=None):
    """Read a fixture at its pinned commit.

    The fixtures live with the harness rather than on the default branch, so
    the shipped action does not carry test files. The harness recreates the
    fixture branches from them on every run, which is what lets the fixture
    pull requests be closed without breaking the suite.

    When the scenario pins a commit SHA, the file is read from that commit
    rather than from the working tree. A branch can move, so reading from it
    would let an accidental push change the scenario under test; a commit
    cannot move.

    Returns ``(content, path, sha)``. The content is None when the fixture
    has no file or its SHA is not pinned yet.
    """
    relative = FIXTURE_FILES.get(scenario.name)
    sha = scenario_registry.FIXTURE_SHAS.get(scenario.name)

    if relative is None:
        # The provider-failure scenario is a workflow configuration change
        # rather than a file fixture, so it has no content to apply. Its
        # branch is recreated from the source ref like the others.
        return None, None, sha

    path = "e2e/fixtures/%s" % relative
    if sha and repo:
        content = gh_client.read_file_at_ref(repo, sha, path)
        if content is not None:
            return content, path, sha

    local = os.path.join(FIXTURES_DIR, relative)
    if not os.path.exists(local):
        return None, path, sha
    with open(local) as handle:
        return handle.read(), path, sha


def recreate_fixtures(repo, scenarios, source_ref, log=print):
    """Rebuild the fixture branches from the reference under test.

    Each branch is created from ``source_ref`` and receives the fixture file,
    so the code the reviewer executes comes from the reference under test and
    the diff it reviews is exactly the fixture change.

    The provider-failure scenario has no source fixture: it is a workflow
    configuration change, so it is left alone.
    """
    recreated = []
    for scenario in scenarios:
        content, fixture_path, _sha = _fixture_content(scenario, repo)
        if content is None:
            log("  %s: no fixture file, leaving the branch untouched" % scenario.name)
            continue
        sha = gh_client.apply_fixture_commit(
            repo, scenario.branch, source_ref, fixture_path, content,
            "[e2e] recreate %s fixture from %s" % (scenario.name, source_ref),
        )
        recreated.append((scenario.name, sha[:7]))
        log("  %s: fixture branch rebuilt from %s (%s)"
            % (scenario.name, source_ref, sha[:7]))
    return recreated


def run_scenario(scenario, repo, base_branch, ref, timeout=480, artifacts_dir=None,
                 log=print, provider_retries=1):
    """Run one scenario, retrying only on a provider outage.

    A provider outage is infrastructure, not a product defect, and it resolves
    on its own once the quota window resets. Model variance does not: a
    finding that the model missed is a real signal about the model, so it is
    never retried.
    """
    # A scenario-specific timeout wins over the suite default.
    effective_timeout = scenario.timeout or timeout
    attempts = provider_retries + 1

    for attempt in range(attempts):
        started = time.time()
        result = _execute(scenario, repo, base_branch, effective_timeout, log)
        result.elapsed = time.time() - started

        if result.status != "PASS" and artifacts_dir:
            path = _write_artifact(artifacts_dir, scenario, result.body, ref)
            if path:
                result.artifact = path

        if result.passed or result.failure_class != "provider":
            return result

        if attempt + 1 >= attempts:
            return result

        log("  provider unavailable, retrying in %ds (%d/%d)"
            % (PROVIDER_RETRY_DELAY, attempt + 1, attempts - 1))
        time.sleep(PROVIDER_RETRY_DELAY)
        # A retry needs a fresh run, since the previous one hit the outage.
        try:
            gh_client.push_empty_commit(
                repo, scenario.branch,
                "[e2e] retry %s after provider outage" % scenario.name,
            )
        except gh_client.GhError as error:
            log("  could not trigger a retry: %s" % error)
            return result

    return result


def _execute(scenario, repo, base_branch, timeout, log):
    pr_number = None
    body = None

    try:
        content, fixture_path, fixture_sha = _fixture_content(scenario, repo)
        if not fixture_sha:
            # The fixture has no pinned commit, so the scenario cannot run
            # deterministically yet.
            return Result(
                scenario, "SKIPPED", pr_number=None,
                failure_class="unpinned",
                error="the fixture for %s has no pinned commit SHA, so the "
                      "scenario cannot run deterministically yet" % scenario.name,
            )
        pr_number, created = gh_client.ensure_pr(
            repo, scenario, base_branch,
            fixture_content=content, fixture_path=fixture_path, log=log,
        )
        log("  %s pull request #%s" % ("created" if created else "reused", pr_number))
        if fixture_sha:
            log("  fixture pinned at %s" % fixture_sha[:7])

        # Block on the workflow for the current head commit before reading any
        # comment. A pull request keeps the comment from its previous run, so
        # reading first would grade a stale result.
        sha = gh_client.head_sha(repo, scenario.branch)
        run, run_elapsed = gh_client.wait_for_ci_run(repo, sha, timeout=timeout, log=log)

        if run is None:
            # No run for this commit yet. Trigger one instead of failing: an
            # empty commit on the fixture branch is enough to start the
            # workflow, and it leaves the reviewed diff untouched.
            log("  no CI run for %s, triggering one" % sha[:7])
            gh_client.push_empty_commit(
                repo, scenario.branch,
                "[e2e] trigger review for %s" % scenario.name,
            )
            sha = gh_client.head_sha(repo, scenario.branch)
            run, run_elapsed = gh_client.wait_for_ci_run(repo, sha, timeout=timeout, log=log)
            if run is None:
                raise gh_client.GhError(
                    "no CI run started for %s after triggering one" % sha[:7]
                )

        if run.get("conclusion") not in ("success", None):
            # The verdict comes from the comment, never from the run status:
            # a qa branch always fails its QA merge guard by design, and the
            # provider-failure scenario fails its run on purpose.
            log("  Examples CI concluded %s after %ds (the verdict comes from "
                "the comment, not from this status)"
                % (run.get("conclusion"), int(run_elapsed)))

        # Only accept a comment published by this run, otherwise the previous
        # run's comment is graded as the current result.
        not_before = run.get("startedAt") or run.get("createdAt") or ""

        comment, wait_elapsed = gh_client.wait_for_review(
            repo, pr_number, timeout=timeout, log=log, not_before=not_before
        )
        body = comment.get("body") or ""
        comment_id = comment["id"]
        log("  comment %s after %ds" % (comment_id, int(wait_elapsed)))

        if scenario.requires_synchronize:
            log("  pushing an empty commit to trigger synchronize")
            gh_client.push_empty_commit(
                repo, scenario.branch,
                "[e2e] trigger synchronize for %s" % scenario.name,
            )
            # Wait for the second run before checking that the comment was
            # updated, otherwise the previous body would be graded again.
            second_sha = gh_client.head_sha(repo, scenario.branch)
            gh_client.wait_for_ci_run(repo, second_sha, timeout=timeout, log=log)
            comment, update_elapsed = gh_client.wait_for_update(
                repo, pr_number, comment_id,
                previous_updated_at=comment.get("updated_at") or "",
                timeout=timeout, log=log,
            )
            log("  comment updated in place after %ds" % int(update_elapsed))
            # Grade the updated comment. The second run is what the scenario
            # is about, and it may have found something the first run did
            # not, or lost the review entirely.
            body = comment.get("body") or ""

        checks = scenario.run_assertions(body)

        # A scenario expecting a valid review must not be failed against the
        # structural checks when the action could not reach the provider at
        # all. Tag those as provider so the report blames the outage rather
        # than the product, which is what the failure classification is for.
        #
        # The action checks are kept: a duplicated marker alongside an outage
        # is still a defect of the action, and dropping it would let the
        # defect disappear once a retry succeeded.
        if not scenario.expect_provider_failure and assertions.is_provider_failure(body):
            for check in checks:
                if check.failure_class == "action":
                    check.failure_class = "provider"
            checks.append(assertions.check(
                "provider reachable",
                False,
                "the action reported the provider as unavailable: %s"
                % _provider_message(body),
                failure_class="provider",
            ))

        published = gh_client.cra_comments(repo, pr_number)
        checks.append(assertions.check(
            "exactly one CRA comment",
            len(published) == 1,
            "found %d CRA comments" % len(published),
            failure_class="action",
        ))

        failed = [check for check in checks if not check.ok]
        if not failed:
            return Result(scenario, "PASS", checks=checks, pr_number=pr_number,
                          comment_id=comment_id, body=body)

        return Result(
            scenario, "FAIL", checks=checks, pr_number=pr_number,
            comment_id=comment_id, failure_class=assertions.classify_failure(checks),
            body=body,
        )

    except gh_client.GhError as error:
        return Result(scenario, "FAIL", pr_number=pr_number, failure_class="action",
                      error=str(error), body=body)

    except TimeoutError as error:
        # The comment never arrived. That is either the action failing to run
        # or the provider never responding; the artifact, when present,
        # distinguishes them.
        return Result(scenario, "FAIL", pr_number=pr_number,
                      failure_class="action", error=str(error), body=body)

    except Exception as error:  # noqa: BLE001 - a bug in the harness itself
        return Result(scenario, "ERROR", pr_number=pr_number,
                      failure_class="harness",
                      error="%s\n%s" % (error, traceback.format_exc()),
                      body=body)