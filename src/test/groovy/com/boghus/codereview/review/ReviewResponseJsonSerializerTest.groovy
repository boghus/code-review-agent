package com.boghus.codereview.review

import groovy.json.JsonSlurper
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseJsonSerializerTest {

    @Test
    void 'serializes a review response using the stable json contract'() {
        ReviewResponse response = new ReviewResponse(
            'Se encontraron problemas que requieren revisión.',
            [
                new ReviewFinding(
                    ReviewSeverity.HIGH,
                    'Manejo incorrecto de errores',
                    'src/PaymentService.groovy',
                    124,
                    'La excepción se captura sin conservar el contexto original.',
                    'Puede ocultar la causa real del fallo.',
                    'Conservar la causa original al propagar la excepción.'
                ),
                new ReviewFinding(
                    ReviewSeverity.LOW,
                    'Lógica duplicada',
                    'src/UserService.groovy',
                    null,
                    'La misma validación aparece en más de un camino.',
                    null,
                    'Extraer la validación a un método común.'
                )
            ]
        )

        Map<String, Object> json = (Map<String, Object>) new JsonSlurper().parseText(
            ReviewResponseJsonSerializer.toJson(response)
        )

        assertThat(json)
            .containsEntry('summary', 'Se encontraron problemas que requieren revisión.')
            .containsKey('findings')

        List<Map<String, Object>> findings = (List<Map<String, Object>>) json.findings
        assertThat(findings).hasSize(2)
        assertThat(findings[0])
            .containsEntry('severity', 'HIGH')
            .containsEntry('title', 'Manejo incorrecto de errores')
            .containsEntry('file', 'src/PaymentService.groovy')
            .containsEntry('line', 124)
            .containsEntry('description', 'La excepción se captura sin conservar el contexto original.')
            .containsEntry('impact', 'Puede ocultar la causa real del fallo.')
            .containsEntry('recommendation', 'Conservar la causa original al propagar la excepción.')

        assertThat(findings[1])
            .containsEntry('severity', 'LOW')
            .containsEntry('line', null)
            .containsEntry('impact', null)
    }

    @Test
    void 'serializes an empty findings list'() {
        ReviewResponse response = new ReviewResponse('No se encontraron hallazgos.', [])

        Map<String, Object> json = (Map<String, Object>) new JsonSlurper().parseText(
            ReviewResponseJsonSerializer.toJson(response)
        )

        assertThat(json)
            .containsEntry('summary', 'No se encontraron hallazgos.')
            .containsEntry('findings', [])
    }

    @Test
    void 'rejects a null response'() {
        assertThat({ ReviewResponseJsonSerializer.toJson(null) })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage('Review response must not be null.')
    }

    @Test
    void 'rejects a response without a summary'() {
        assertThat({ new ReviewResponse(null, []) })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review response field 'summary' must not be null.")
    }

    @Test
    void 'rejects a finding without a severity'() {
        assertThat({
            new ReviewFinding(null, 'Title', 'Foo.groovy', 10, 'Description', null, null)
        })
            .isInstanceOf(IllegalArgumentException)
            .hasMessage("Review finding field 'severity' must not be null.")
    }
}
