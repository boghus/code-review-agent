package qa.fixtures

/**
 * Harmless fixture used to trigger a second review run on the same PR.
 * The content intentionally contains no review findings.
 */
class IdempotencyFixture {
    static String message() {
        'second review run'
    }
}
