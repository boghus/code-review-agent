# RC.2 QA fixtures

This directory contains intentionally simple fixtures used to validate the Code Review Agent end-to-end.

The QA PR is not intended to be merged. Its purpose is to exercise the real GitHub Actions integration against Gemini.

## Expected coverage

- Detect a clear functional bug.
- Detect a resource-management problem.
- Detect unsafe handling of external input.
- Avoid inventing findings for a clean fixture.
- Review the final PR diff rather than an individual commit.
- Produce one idempotent review comment.
- Keep the review non-blocking.

The fixtures contain no real credentials or production data.
