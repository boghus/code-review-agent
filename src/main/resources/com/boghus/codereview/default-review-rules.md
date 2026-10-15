# Code Review Agent by boghus — Default Review Rules

These rules are the built-in review contract used when a repository does not configure `rules-path`. Focus on defects that can affect correctness, security, reliability, maintainability, or users.

## Review priorities

1. **Correctness and logic**
   - Look for incorrect conditions, boundary errors, invalid assumptions, broken state transitions, and regressions in existing behavior.
   - Check nullability, empty inputs, missing records, duplicate data, time zones, rounding, and other edge cases when relevant to the changed code.
   - Verify that return values, exceptions, and side effects match the intended behavior.

2. **Security and privacy**
   - Flag hardcoded secrets, credential exposure, injection risks, unsafe deserialization, path traversal, and missing authorization or tenant isolation.
   - Check that untrusted input is validated at trust boundaries and that sensitive data is not written to logs or returned to unauthorized callers.
   - Do not claim a vulnerability without pointing to the code path that makes it possible.

3. **Data integrity and persistence**
   - Check transaction boundaries, partial failures, duplicate processing, idempotency, race-prone writes, and consistency between related records.
   - For schema or query changes, consider existing data, null values, indexes, migration safety, and compatibility with deployed versions.
   - Flag destructive or irreversible operations when safeguards or recovery paths are missing.

4. **Performance and resource usage**
   - Look for database or network calls inside loops, N+1 queries, unbounded work, avoidable repeated computation, inefficient queries, and missing pagination or limits.
   - Check resource lifecycle management, including streams, connections, locks, and temporary files.
   - Report performance concerns only when the changed code provides a plausible path to meaningful impact.

5. **Concurrency and reliability**
   - Look for unsynchronized shared mutable state, race conditions, unsafe retries, duplicate side effects, deadlocks, and incorrect assumptions about execution order.
   - Check timeouts, error handling, retry behavior, and failure paths around external services.
   - Retries should be bounded and should not repeat non-idempotent operations without protection.

6. **Compatibility and maintainability**
   - Identify breaking API, configuration, data-format, or behavior changes that are not reflected in callers, tests, or documentation.
   - Prefer simple, focused changes that fit the surrounding architecture; avoid speculative redesign requests.
   - Check that important behavior is covered by tests, especially edge cases and regressions introduced by the change.

## Evidence and findings quality

- Report only actionable issues introduced by, or directly exposed by, the pull request.
- Every finding must cite the relevant changed file and line when possible, explain a concrete failure scenario, and state its likely impact.
- Suggest a practical fix when the evidence supports one.
- Do not invent bugs, assume requirements that are not present, or report hypothetical concerns as confirmed defects.
- Do not flag formatting, naming preferences, or subjective style differences unless they cause a concrete problem or violate an explicit project convention.
- Avoid duplicate findings; combine symptoms that share the same root cause.
- Use severity proportionally to the demonstrated impact and likelihood. A finding's severity is a prioritization aid, not a guarantee.
- If evidence is insufficient to establish a defect, omit the finding.
