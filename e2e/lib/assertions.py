"""Deterministic assertions over a Code Review Agent comment body.

Every assertion in this module targets output produced by
``ReviewResponseMarkdownRenderer`` / ``ReviewReportWriter``, never by the LLM.
The model supplies the prose inside ``summary``, ``title``, ``description``,
``impact`` and ``recommendation``; the renderer supplies the marker, the
heading, the finding count, the severity labels, the file/line reference and
the severity summary line. Asserting renderer output keeps the suite stable
across model, version and temperature changes.

``APPROVE`` / ``CHANGES_REQUESTED`` are deliberately not assertable: the
product emits no structured verdict. See ``e2e/README.md``.
"""

import re

MARKER = "<!-- code-review-agent-by-boghus -->"
LEGACY_MARKER = "<!-- code-review-agent -->"

SEVERITIES = ("CRITICAL", "HIGH", "MEDIUM", "LOW")
SEVERITY_ICONS = {"CRITICAL": "🔴", "HIGH": "🟠", "MEDIUM": "🟡", "LOW": "🔵"}

AI_HEADING = "## 🤖 Code Review Agent"
UNAVAILABLE_HEADING = "unavailable"
SKIPPED_HEADING = "skipped"

SEVERITY_ORDER = ("CRITICAL", "HIGH", "MEDIUM", "LOW")

# Renderer-emitted counts, e.g.
# **Severity summary:** 🔴 0 CRITICAL · 🟠 1 HIGH · 🟡 0 MEDIUM · 🔵 0 LOW
SEVERITY_SUMMARY_RE = re.compile(
    r"\*\*Severity summary:\*\*\s+🔴\s*(\d+)\s+CRITICAL\s*·\s*🟠\s*(\d+)\s+HIGH"
    r"\s*·\s*🟡\s*(\d+)\s+MEDIUM\s*·\s*🔵\s*(\d+)\s+LOW"
)
# Spanish equivalent emitted when the action runs with language=es.
SEVERITY_SUMMARY_RE_ES = re.compile(
    r"\*\*Resumen de severidad:\*\*\s+🔴\s*(\d+)\s+CRITICAL\s*·\s*🟠\s*(\d+)\s+HIGH"
    r"\s*·\s*🟡\s*(\d+)\s+MEDIUM\s*·\s*🔵\s*(\d+)\s+LOW"
)

# Renderer-emitted finding count. The English wording is
# "Found **3 findings**" / "Found **1 finding**"; the Spanish wording is
# "Encontramos **3 hallazgos**" / "Encontramos **1 hallazgo**".
FINDINGS_COUNT_RE = re.compile(
    r"(?:Found\s+\*\*(\d+)\s+finding|Encontramos\s+\*\*(\d+)\s+hallazgo)", re.IGNORECASE
)

NO_FINDINGS_RE = re.compile(r"\*\*(?:No findings|Sin hallazgos)\.\*\*")

# Per-finding severity label, e.g. "🟠 **HIGH — Some title**"
FINDING_SEVERITY_RE = re.compile(
    r"(" + "|".join(SEVERITY_ICONS.values()) + r")\s*\*\*\s*(CRITICAL|HIGH|MEDIUM|LOW)\s+—"
)

# Location block, e.g. "`src/Foo.groovy:42`". The renderer emits the location
# as a standalone backticked token on its own line, so anchoring to a line that
# contains only that token avoids matching prose such as
# "`https://reporting.internal/v1/ingest`", which would otherwise be read as a
# file named "https" at some line.
LOCATION_RE = re.compile(
    r"^`([^`\n]*?):(\d+)`\s*$",
    re.MULTILINE,
)

# Patterns that must never survive into a published comment.
SECRET_PATTERNS = (
    re.compile(r"AIza[0-9A-Za-z\-_]{10,}"),
    re.compile(r"\bsk-[0-9A-Za-z\-_]{10,}"),
    re.compile(r"\bghp_[0-9A-Za-z]{10,}"),
    re.compile(r"\bgithub_pat_[0-9A-Za-z_]{10,}"),
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
)


class Check(object):
    """A single assertion outcome.

    ``ok`` decides PASS/FAIL. ``failure_class`` distinguishes the three causes
    the suite must tell apart, so a FAIL is actionable without a retry:
    ``action`` (bug in the action), ``provider`` (infrastructure) or
    ``llm_variance`` (report is well formed, expected finding absent).
    """

    def __init__(self, name, ok, detail="", failure_class=""):
        self.name = name
        self.ok = bool(ok)
        self.detail = detail
        self.failure_class = failure_class

    def __repr__(self):
        state = "ok" if self.ok else "FAIL"
        extra = " [%s]" % self.failure_class if self.failure_class else ""
        suffix = ": %s" % self.detail if self.detail else ""
        return "<Check %s %s%s%s>" % (state, self.name, extra, suffix)


def check(name, ok, detail="", failure_class=""):
    return Check(name, ok, detail, failure_class)


def marker_count(body):
    """Number of occurrences of the current marker.

    Uses str.count rather than a regex because the marker contains characters
    that are meaningful in a pattern.
    """
    return body.count(MARKER)


def has_legacy_marker(body):
    return LEGACY_MARKER in body


def is_provider_failure(body):
    """True when the comment reports the action as unavailable.

    Only the "unavailable" branch is a provider outage. The "skipped" branch
    covers a configuration or rules problem, which is a defect in the setup
    rather than an infrastructure outage, and it must not be retried as one.
    """
    if MARKER not in body:
        return False
    return "Code Review Agent by boghus unavailable" in body


def is_skipped(body):
    """True when the action skipped the review for a setup reason."""
    if MARKER not in body:
        return False
    return "Code Review Agent by boghus skipped" in body


def is_non_ai_report(body):
    """True for any renderer branch that is not a valid AI review."""
    if MARKER not in body:
        return True
    if is_provider_failure(body):
        return True
    if "No changes to review" in body:
        return True
    return AI_HEADING not in body


def parse_severity_counts(body):
    """Return the severity counts as a tuple of ints, or None when absent.

    The line is produced from ``findings.size()`` by the renderer, so the
    numbers are deterministic even though the surrounding prose is not.
    """
    match = SEVERITY_SUMMARY_RE.search(body) or SEVERITY_SUMMARY_RE_ES.search(body)
    if match is None:
        return None
    return tuple(int(group) for group in match.groups())


def parse_findings_count(body):
    """Return the renderer-emitted finding count, or None when absent."""
    match = FINDINGS_COUNT_RE.search(body)
    if match is None:
        return None
    return int(match.group(1) or match.group(2))


def parse_finding_severities(body):
    """Return the severities of the rendered findings, in document order."""
    return [match.group(2) for match in FINDING_SEVERITY_RE.finditer(body)]


def parse_locations(body):
    """Return the ``(file, line)`` pairs the renderer emitted."""
    return [(match.group(1), int(match.group(2))) for match in LOCATION_RE.finditer(body)]


def find_secrets(body):
    """Return the secret-looking substrings that leaked into the comment."""
    leaked = []
    for pattern in SECRET_PATTERNS:
        leaked.extend(match.group(0) for match in pattern.finditer(body))
    return leaked


def expected_severity_line(counts):
    """Render the exact severity summary line for a tuple of counts."""
    parts = []
    for severity, count in zip(SEVERITY_ORDER, counts):
        parts.append("%s %d %s" % (SEVERITY_ICONS[severity], count, severity))
    return "**Severity summary:** " + " · ".join(parts)


def assert_identity(body):
    """Marker and structural checks that apply to every scenario."""
    checks = [check("marker present", MARKER in body)]

    count = marker_count(body)
    checks.append(
        check(
            "marker appears exactly once",
            count == 1,
            "found %d occurrences" % count,
            failure_class="action",
        )
    )

    if has_legacy_marker(body):
        checks.append(
            check(
                "legacy marker absent",
                False,
                "legacy marker %r is still published" % LEGACY_MARKER,
                failure_class="action",
            )
        )

    leaked = find_secrets(body)
    checks.append(
        check(
            "no secrets in comment",
            not leaked,
            "leaked: %s" % ", ".join(leaked[:3]) if leaked else "",
            failure_class="action",
        )
    )
    return checks


def assert_valid_review(body, expected_counts=None, expected_files=None,
                        min_findings=None, expect_severity=None,
                        min_counts=None):
    """Assert a valid AI review with optional expectations.

    ``expected_counts`` is the exact severity tuple the renderer must have
    emitted. Use it only where the fixture pins the outcome, such as ``clean``
    and ``idempotency``, where a single extra finding is a real regression.

    ``min_counts`` asserts a lower bound per severity instead. Use it for the
    severity scenarios: a reviewer that finds the planted secret *and* another
    legitimate problem has passed, so pinning the exact tuple would fail a
    correct review. ``critical`` expecting ``(1, 0, 0, 0)`` would fail because
    the model also flagged a missing HTTP timeout, which is a true finding.

    ``expect_severity`` requires at least one finding of that severity.
    ``expected_files`` restricts ``file:line`` references to the fixture's own
    files.
    """
    checks = list(assert_identity(body))

    # When the action could not reach the provider it renders no severity
    # summary, so every structural check below fails. Tag those as provider so
    # the classification blames the outage instead of the product.
    outage = is_provider_failure(body)
    structural_class = "provider" if outage else "action"

    checks.append(
        check(
            "valid review branch",
            not is_non_ai_report(body),
            "expected the AI review branch, got a non-AI branch (provider failure, "
            "empty diff or oversized diff)",
            failure_class=structural_class,
        )
    )

    counts = parse_severity_counts(body)
    if counts is None:
        checks.append(
            check(
                "severity summary present",
                False,
                "no severity summary line was rendered",
                failure_class=structural_class,
            )
        )
    else:
        if expected_counts is not None:
            checks.append(
                check(
                    "severity summary matches fixture",
                    counts == tuple(expected_counts),
                    "expected %s, got %s"
                    % (expected_severity_line(expected_counts),
                       expected_severity_line(counts)),
                    failure_class="llm_variance",
                )
            )
        if min_counts is not None:
            for index, severity in enumerate(SEVERITY_ORDER):
                needed = min_counts[index]
                if needed and counts[index] < needed:
                    checks.append(
                        check(
                            "at least %d %s finding(s)" % (needed, severity),
                            False,
                            "rendered %d" % counts[index],
                            failure_class="llm_variance",
                        )
                    )

    total = sum(counts) if counts is not None else None
    if min_findings is not None:
        checks.append(
            check(
                "at least %d finding(s)" % min_findings,
                total is not None and total >= min_findings,
                "rendered %s findings" % (total if total is not None else "an unknown number of"),
                failure_class="llm_variance",
            )
        )

    if expect_severity is not None:
        present = expect_severity in parse_finding_severities(body)
        checks.append(
            check(
                "at least one %s finding" % expect_severity,
                present,
                "rendered severities: %s"
                % (parse_finding_severities(body) or "none"),
                failure_class="llm_variance",
            )
        )

    if expected_files is not None:
        locations = parse_locations(body)
        unexpected = [loc for loc in locations if loc[0] not in expected_files]
        checks.append(
            check(
                "finding locations within fixture",
                not unexpected,
                "unexpected: %s" % unexpected[:3] if unexpected else "",
                failure_class="llm_variance",
            )
        )
        # A review with no location at all would pass the check above, since
        # there is nothing unexpected in an empty list. The scenarios that
        # pin a file expect the finding to point at it.
        checks.append(
            check(
                "finding locations present",
                bool(locations),
                "no file:line reference was rendered",
                failure_class="llm_variance",
            )
        )

    rendered = parse_findings_count(body)
    if counts is not None:
        if rendered is None:
            checks.append(check("finding count line present", counts == (0, 0, 0, 0)))
        else:
            checks.append(
                check(
                    "finding count line agrees with severity summary",
                    rendered == sum(counts),
                    "line says %d, severity summary totals %d" % (rendered, sum(counts)),
                    failure_class="action",
                )
            )
    return checks


def assert_clean_review(body):
    """The ``clean`` scenario: zero findings across all four severities."""
    checks = assert_valid_review(body, expected_counts=(0, 0, 0, 0))
    checks.append(
        check(
            "renders no-findings notice",
            NO_FINDINGS_RE.search(body) is not None,
            "expected '**No findings.**' or '**Sin hallazgos.**'",
            failure_class="llm_variance",
        )
    )
    checks.append(
        check(
            "renders no per-finding severity labels",
            not parse_finding_severities(body),
            "rendered: %s" % parse_finding_severities(body),
            failure_class="llm_variance",
        )
    )
    return checks


def assert_provider_failure(body):
    """The ``provider-failure`` scenario: a controlled, non-blocking error."""
    checks = list(assert_identity(body))
    checks.append(check("provider failure branch", is_provider_failure(body)))
    checks.append(
        check(
            "states review is non-blocking",
            "non-blocking" in body,
            "expected the comment to say the review is non-blocking",
            failure_class="action",
        )
    )
    checks.append(
        check(
            "publishes no findings",
            parse_severity_counts(body) is None
            and not parse_finding_severities(body),
            "a provider failure must not render findings",
            failure_class="action",
        )
    )
    return checks


def classify_failure(checks):
    """Pick the single most informative failure class from failing checks.

    ``provider`` is checked first. When the action cannot reach the provider it
    renders no severity summary at all, which would otherwise surface as
    ``action`` through the structural checks and read as a product defect.
    """
    priority = ("provider", "action", "llm_variance")
    failing = [item for item in checks if not item.ok and item.failure_class]
    for name in priority:
        for item in failing:
            if item.failure_class == name:
                return item.failure_class
    return ""