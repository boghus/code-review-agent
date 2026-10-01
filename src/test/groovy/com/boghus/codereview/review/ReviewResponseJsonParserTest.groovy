package com.boghus.codereview.review

import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

class ReviewResponseJsonParserTest {

    @Test
    void 'parses the review response returned by the model'() {
        String json = '''{
            "summary": "Resumen de la revisión: 0 hallazgos CRITICAL, 0 hallazgos HIGH, 1 hallazgo MEDIUM, 0 hallazgos LOW.",
            "findings": [
                {
                    "severity": "MEDIUM",
                    "title": "El generador de esquema no filtra campos transitorios",
                    "file": "src/main/groovy/com/boghus/codereview/review/ReviewResponseSchemaGenerator.groovy",
                    "line": 47,
                    "description": "El método schemaFor solo filtra campos estáticos y sintéticos.",
                    "impact": "Campos internos o transitorios podrían incluirse inesperadamente.",
                    "recommendation": "Agregar la comprobación !Modifier.isTransient(field.modifiers)."
                }
            ]
        }'''

        ReviewResponse response = ReviewResponseJsonParser.parse(json)

        assertThat(response.summary)
            .isEqualTo('Resumen de la revisión: 0 hallazgos CRITICAL, 0 hallazgos HIGH, 1 hallazgo MEDIUM, 0 hallazgos LOW.')
        assertThat(response.findings).hasSize(1)
        assertThat(response.findings[0].severity).isEqualTo(ReviewSeverity.MEDIUM)
        assertThat(response.findings[0].title)
            .isEqualTo('El generador de esquema no filtra campos transitorios')
        assertThat(response.findings[0].file)
            .isEqualTo('src/main/groovy/com/boghus/codereview/review/ReviewResponseSchemaGenerator.groovy')
        assertThat(response.findings[0].line).isEqualTo(47)
        assertThat(response.findings[0].description)
            .isEqualTo('El método schemaFor solo filtra campos estáticos y sintéticos.')
        assertThat(response.findings[0].impact)
            .isEqualTo('Campos internos o transitorios podrían incluirse inesperadamente.')
        assertThat(response.findings[0].recommendation)
            .isEqualTo('Agregar la comprobación !Modifier.isTransient(field.modifiers).')
    }

    @Test
    void 'parses a response without findings'() {
        ReviewResponse response = ReviewResponseJsonParser.parse(
            '{"summary":"No se encontraron hallazgos.","findings":[]}'
        )

        assertThat(response.findings).isEmpty()
    }

    @Test
    void 'parses nullable finding fields'() {
        ReviewResponse response = ReviewResponseJsonParser.parse(
            '{"summary":"Hallazgo parcial.","findings":[{"severity":"LOW","title":"Duplicación","file":null,"line":null,"description":"Descripción","impact":null,"recommendation":null}]}'
        )

        ReviewFinding finding = response.findings[0]

        assertThat(finding.file).isNull()
        assertThat(finding.line).isNull()
        assertThat(finding.impact).isNull()
        assertThat(finding.recommendation).isNull()
    }

    @Test
    void 'rejects empty json'() {
        assertThatThrownBy({ ReviewResponseJsonParser.parse('') })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage('Review response JSON must not be empty.')
    }

    @Test
    void 'rejects a non-object json response'() {
        assertThatThrownBy({ ReviewResponseJsonParser.parse('[]') })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage('Review response JSON must be an object.')
    }

    @Test
    void 'rejects malformed json'() {
        assertThatThrownBy({ ReviewResponseJsonParser.parse('{"summary":') })
            .isInstanceOf(Exception)
    }

    @Test
    void 'rejects a missing summary'() {
        assertThatThrownBy({
            ReviewResponseJsonParser.parse('{"findings":[]}')
        })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review response field 'summary' must be a non-empty string.")
    }

    @Test
    void 'rejects a missing findings array'() {
        assertThatThrownBy({
            ReviewResponseJsonParser.parse('{"summary":"Resumen"}')
        })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review response field 'findings' must be an array.")
    }

    @Test
    void 'rejects a finding that is not an object'() {
        assertThatThrownBy({
            ReviewResponseJsonParser.parse('{"summary":"Resumen","findings":["invalid"]}')
        })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage('Each review finding must be an object.')
    }

    @Test
    void 'rejects an invalid severity'() {
        assertThatThrownBy({
            ReviewResponseJsonParser.parse('{"summary":"Resumen","findings":[{"severity":"BANANA","title":"Título","description":"Descripción"}]}')
        })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Invalid review finding severity: 'BANANA'.")
    }

    @Test
    void 'rejects a missing finding title'() {
        assertThatThrownBy({
            ReviewResponseJsonParser.parse('{"summary":"Resumen","findings":[{"severity":"HIGH","description":"Descripción"}]}')
        })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review response field 'title' must be a non-empty string.")
    }

    @Test
    void 'rejects a missing finding description'() {
        assertThatThrownBy({
            ReviewResponseJsonParser.parse('{"summary":"Resumen","findings":[{"severity":"HIGH","title":"Título"}]}')
        })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review response field 'description' must be a non-empty string.")
    }

    @Test
    void 'rejects a finding with a non-integer line'() {
        assertThatThrownBy({
            ReviewResponseJsonParser.parse('{"summary":"Resumen","findings":[{"severity":"HIGH","title":"Título","line":"47","description":"Descripción"}]}')
        })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review response field 'line' must be an integer or null.")
    }

    @Test
    void 'rejects a finding with a non-string optional field'() {
        assertThatThrownBy({
            ReviewResponseJsonParser.parse('{"summary":"Resumen","findings":[{"severity":"HIGH","title":"Título","file":47,"description":"Descripción"}]}')
        })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review response field 'file' must be a string or null.")
    }
}
