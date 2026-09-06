package qa.fixtures

class CleanQaFixture {

    static String normalizeName(String name) {
        name?.trim()?.toUpperCase()
    }

    static List<String> sortNames(List<String> names) {
        names.findAll { it != null }
                .collect { it.trim() }
                .sort()
    }
}
