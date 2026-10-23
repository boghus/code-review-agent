"""Scenario definitions for the Code Review Agent E2E suite.

Each scenario maps to a ``qa/e2e-<name>`` branch in this repository and to the
assertions the comment must satisfy. See ``e2e/README.md`` for the contract
these encode.
"""

from assertions import (
    assert_clean_review,
    assert_provider_failure,
    assert_valid_review,
)

BASE_BRANCH = "main"
BRANCH_PREFIX = "qa/e2e-"


class Scenario(object):
    def __init__(self, name, title, expected_counts=None, expected_files=None,
                 min_findings=None, expect_severity=None, expect_clean=False,
                 expect_provider_failure=False, requires_synchronize=False,
                 timeout=None, min_counts=None, extra_assert=None):
        self.name = name
        self.title = title
        self.expected_counts = expected_counts
        self.expected_files = expected_files
        self.min_findings = min_findings
        self.expect_severity = expect_severity
        self.expect_clean = expect_clean
        self.expect_provider_failure = expect_provider_failure
        self.requires_synchronize = requires_synchronize
        # Per-scenario override of the suite timeout, for scenarios that are
        # expected to be slower than the rest.
        self.timeout = timeout
        self.min_counts = min_counts
        self.extra_assert = extra_assert

    @property
    def branch(self):
        return BRANCH_PREFIX + self.name

    def run_assertions(self, body):
        """Return the list of checks for a published comment body."""
        if self.expect_provider_failure:
            return assert_provider_failure(body)
        if self.expect_clean:
            return assert_clean_review(body)
        checks = assert_valid_review(
            body,
            expected_counts=self.expected_counts,
            expected_files=self.expected_files,
            min_findings=self.min_findings,
            expect_severity=self.expect_severity,
            min_counts=self.min_counts,
        )
        if self.extra_assert is not None:
            checks.extend(self.extra_assert(body))
        return checks


def _fixture_file(name):
    return "e2e/fixtures/%s" % name


SCENARIOS = [
    Scenario(
        "clean",
        "clean change yields zero findings",
        expect_clean=True,
    ),
    Scenario(
        "critical",
        "hardcoded secret yields a CRITICAL finding",
        min_counts=(1, 0, 0, 0),
        expected_files={_fixture_file("critical/ConfigFixture.groovy")},
        min_findings=1,
        expect_severity="CRITICAL",
    ),
    Scenario(
        "high",
        "functional bug yields a HIGH finding",
        min_counts=(0, 1, 0, 0),
        expected_files={_fixture_file("high/CounterFixture.groovy")},
        min_findings=1,
        expect_severity="HIGH",
    ),
    Scenario(
        "medium",
        "duplication without tests yields a MEDIUM finding",
        min_counts=(0, 0, 1, 0),
        expected_files={_fixture_file("medium/Formatter.groovy")},
        min_findings=1,
        expect_severity="MEDIUM",
    ),
    Scenario(
        "idempotency",
        "re-running updates the comment instead of duplicating it",
        expected_counts=(0, 0, 0, 0),
        requires_synchronize=True,
        # The scenario runs the action twice, so it needs room for both runs
        # plus the empty commit that triggers the second one.
        timeout=900,
        extra_assert=lambda body: [],
    ),
    Scenario(
        "provider-failure",
        "an invalid model yields a non-blocking failure comment",
        expect_provider_failure=True,
    ),
]

SCENARIOS_BY_NAME = {scenario.name: scenario for scenario in SCENARIOS}
SCENARIO_NAMES = [scenario.name for scenario in SCENARIOS]


def get(names=None):
    if not names:
        return list(SCENARIOS)
    unknown = [name for name in names if name not in SCENARIOS_BY_NAME]
    if unknown:
        raise ValueError(
            "unknown scenario(s): %s. Available: %s"
            % (", ".join(unknown), ", ".join(SCENARIO_NAMES))
        )
    return [SCENARIOS_BY_NAME[name] for name in names]