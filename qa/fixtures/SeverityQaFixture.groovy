package qa.fixtures

class SeverityQaFixture {

    // CRITICAL: deliberate secret exposure for severity validation.
    static final String API_KEY = 'sk-test-qa-secret-123456789'

    // HIGH: deliberate functional bug; this method always returns the wrong value.
    static boolean isEnabled(Boolean enabled) {
        enabled && false
    }

    // MEDIUM: deliberate performance issue; repeated linear lookup in a loop.
    static List<String> findNames(List<String> names, List<String> requested) {
        requested.collect { value -> names.find { it == value } }
    }

    static String formatName(String name) {
        String result = name.trim()
        return result
    }
}
