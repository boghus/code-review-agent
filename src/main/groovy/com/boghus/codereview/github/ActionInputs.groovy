package com.boghus.codereview.github

import groovy.transform.CompileStatic

/**
 * Reads inputs supplied to the GitHub Action.
 *
 * The Action contract uses generic names (api-key, model, provider). The
 * concrete environment variable names live here and stay an implementation
 * detail. Tests can construct instances directly.
 */
@CompileStatic
class ActionInputs {

    static final String DEFAULT_PROVIDER = 'gemini'
    static final String DEFAULT_MODEL = 'gemini-3.6-flash'
    static final String DEFAULT_RULES_PATH = '.github/code_review_rules.md'
    static final String DEFAULT_DIFF_PATH = 'cra-pr.diff'
    static final String DEFAULT_OUTPUT_PATH = 'cra-review.md'

    static final int DEFAULT_MAX_DIFF_BYTES = 200_000
    static final int DEFAULT_MAX_DIFF_LINES = 4_000

    String apiKey
    String provider
    String model
    String rulesPath
    String diffPath
    String outputPath
    int maxDiffBytes
    int maxDiffLines

    static ActionInputs fromEnvironment(Map<String, String> env) {
        ActionInputs inputs = new ActionInputs()
        inputs.apiKey = env.CRA_API_KEY
        inputs.provider = env.CRA_PROVIDER ?: DEFAULT_PROVIDER
        inputs.model = env.CRA_MODEL ?: DEFAULT_MODEL
        inputs.rulesPath = env.CRA_RULES_PATH ?: DEFAULT_RULES_PATH
        inputs.diffPath = env.CRA_DIFF_PATH ?: DEFAULT_DIFF_PATH
        inputs.outputPath = env.CRA_OUTPUT_PATH ?: DEFAULT_OUTPUT_PATH
        inputs.maxDiffBytes = InputParser.parsePositiveInt(env.CRA_MAX_DIFF_BYTES, DEFAULT_MAX_DIFF_BYTES)
        inputs.maxDiffLines = InputParser.parsePositiveInt(env.CRA_MAX_DIFF_LINES, DEFAULT_MAX_DIFF_LINES)
        return inputs
    }

    static ActionInputs fromEnv() {
        return fromEnvironment(System.getenv())
    }
}
