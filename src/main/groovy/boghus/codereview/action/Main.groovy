package boghus.codereview.action

class Main {
    static void main(String[] args) {
        requireGeminiApiKey()
        println 'Code Review Agent bootstrap initialized.'
    }

    private static void requireGeminiApiKey() {
        if (!System.getenv('GEMINI_API_KEY')) {
            throw new IllegalArgumentException('GEMINI_API_KEY is required')
        }
    }
}
