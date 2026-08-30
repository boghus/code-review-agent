package com.boghus.codereview.github

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class ActionInputsTest {

    @Test
    void 'applies defaults when env values are absent'() {
        ActionInputs inputs = ActionInputs.fromEnvironment([:])

        assertThat(inputs.provider).isEqualTo(ActionInputs.DEFAULT_PROVIDER)
        assertThat(inputs.model).isEqualTo(ActionInputs.DEFAULT_MODEL)
        assertThat(inputs.rulesPath).isEqualTo(ActionInputs.DEFAULT_RULES_PATH)
        assertThat(inputs.diffPath).isEqualTo(ActionInputs.DEFAULT_DIFF_PATH)
        assertThat(inputs.outputPath).isEqualTo(ActionInputs.DEFAULT_OUTPUT_PATH)
        assertThat(inputs.maxDiffBytes).isEqualTo(ActionInputs.DEFAULT_MAX_DIFF_BYTES)
        assertThat(inputs.maxDiffLines).isEqualTo(ActionInputs.DEFAULT_MAX_DIFF_LINES)
    }

    @Test
    void 'reads CRA_* env values when present'() {
        Map<String, String> env = [
            CRA_API_KEY       : 'secret',
            CRA_PROVIDER      : 'gemini',
            CRA_MODEL         : 'gemini-test',
            CRA_RULES_PATH    : '/tmp/rules.md',
            CRA_DIFF_PATH     : '/tmp/diff',
            CRA_OUTPUT_PATH   : '/tmp/out.md',
            CRA_MAX_DIFF_BYTES: '500000',
            CRA_MAX_DIFF_LINES: '8000'
        ]

        ActionInputs inputs = ActionInputs.fromEnvironment(env)

        assertThat(inputs.apiKey).isEqualTo('secret')
        assertThat(inputs.model).isEqualTo('gemini-test')
        assertThat(inputs.rulesPath).isEqualTo('/tmp/rules.md')
        assertThat(inputs.diffPath).isEqualTo('/tmp/diff')
        assertThat(inputs.outputPath).isEqualTo('/tmp/out.md')
        assertThat(inputs.maxDiffBytes).isEqualTo(500_000)
        assertThat(inputs.maxDiffLines).isEqualTo(8_000)
    }

    @Test
    void 'falls back to defaults for invalid size limits'() {
        Map<String, String> env = [
            CRA_MAX_DIFF_BYTES: 'not-a-number',
            CRA_MAX_DIFF_LINES: '-3'
        ]

        ActionInputs inputs = ActionInputs.fromEnvironment(env)

        assertThat(inputs.maxDiffBytes).isEqualTo(ActionInputs.DEFAULT_MAX_DIFF_BYTES)
        assertThat(inputs.maxDiffLines).isEqualTo(ActionInputs.DEFAULT_MAX_DIFF_LINES)
    }
}
