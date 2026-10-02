package com.boghus.codereview.review

import groovy.json.JsonSlurper
import org.junit.jupiter.api.Test

import static org.assertj.core.api.Assertions.assertThat

class ReviewResponseSchemaGeneratorTest {

    @Test
    void 'generates schema from review response model'() {
        def schema = new JsonSlurper().parseText(ReviewResponseSchemaGenerator.generate())
        def findingSchema = schema.properties.findings.items

        assertThat(schema.type).isEqualTo('object')
        assertThat(schema.required).containsExactly('summary', 'findings')
        assertThat(schema.properties.summary.type).isEqualTo('string')
        assertThat(schema.properties.findings.type).isEqualTo('array')

        assertThat(findingSchema.type).isEqualTo('object')
        assertThat(findingSchema.required)
            .containsExactly('severity', 'title', 'description')
        assertThat(findingSchema.properties.severity.type).isEqualTo('string')
        assertThat(findingSchema.properties.severity.enum)
            .containsExactly('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')
        assertThat(findingSchema.properties.title.type).isEqualTo('string')
        assertThat(findingSchema.properties.file.type)
            .containsExactly('string', 'null')
        assertThat(findingSchema.properties.line.type)
            .containsExactly('integer', 'null')
        assertThat(findingSchema.properties.description.type).isEqualTo('string')
        assertThat(findingSchema.properties.impact.type)
            .containsExactly('string', 'null')
        assertThat(findingSchema.properties.recommendation.type)
            .containsExactly('string', 'null')
    }
}
