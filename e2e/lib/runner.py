"""Scenario runner: execute one scenario and produce a Result."""

import os
import subprocess
import time
import traceback

import assertions
import gh_client
import scenarios as scenario_registry

PROVIDER_FAILURE_CLASS = "provider"


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


def run_scenario(scenario, repo, base_branch, ref, timeout=480, artifacts_dir=None,
                 log=print):
    # A scenario-specific timeout wins over the suite default.
    effective_timeout = scenario.timeout or timeout
    started = time.time()
    result = _execute(scenario, repo, base_branch, effective_timeout, log)

    if result.status != "PASS" and artifacts_dir:
        path = _write_artifact(artifacts_dir, scenario, result.body, ref)
        if path:
            result.artifact = path

    result.elapsed = time.time() - started
    return result


def _execute(scenario, repo, base_branch, timeout, log):
    pr_number = None
    body = None

    try:
        pr_number, created = gh_client.ensure_pr(repo, scenario, base_branch)
        log("  %s pull request #%s" % ("created" if created else "reused", pr_number))

        # Block on the workflow for the current head commit before reading any
        # comment. A pull request keeps the comment from its previous run, so
        # reading first would grade a stale result.
        sha = gh_client.head_sha(repo, scenario.branch)
        run, run_elapsed = gh_client.wait_for_ci_run(repo, sha, timeout=timeout, log=log)
        if run is None:
            log("  no Examples CI run started for %s" % sha[:7])
        elif run.get("conclusion") not in ("success", None):
            # A failed run is not automatically a suite failure: the
            # provider-failure scenario fails its run on purpose, and the
            # qa-merge-guard job always exits non-zero for qa branches.
            log("  Examples CI concluded %s after %ds"
                % (run.get("conclusion"), int(run_elapsed)))

        comment, wait_elapsed = gh_client.wait_for_review(repo, pr_number,
                                                          timeout=timeout, log=log)
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

        checks = scenario.run_assertions(body)

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