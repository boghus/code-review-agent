package com.boghus.codereview.review

import groovy.json.JsonOutput
import groovy.transform.CompileStatic

import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

@CompileStatic
class ReviewResponseSchemaGenerator {

    private static final Set<String> NULLABLE_FIELDS = [
        'file',
        'line',
        'impact',
        'recommendation'
    ] as Set

    static String generate() {
        JsonOutput.toJson(schemaFor(ReviewResponse))
    }

    private static Map<String, Object> schemaFor(Class<?> type) {
        if (type.isEnum()) {
            List<String> enumValues = []
            for (Object constant : type.enumConstants) {
                enumValues << ((Enum) constant).name()
            }

            return [
                type: 'string',
                enum: enumValues
            ]
        }

        if (type == String) {
            return [type: 'string']
        }

        if (type == Integer || type == int) {
            return [type: 'integer']
        }

        Map<String, Object> properties = [:]
        List<String> required = []

        type.declaredFields
            .findAll { java.lang.reflect.Field field ->
                !Modifier.isStatic(field.modifiers) &&
                !Modifier.isTransient(field.modifiers) &&
                !field.synthetic
            }
            .each { java.lang.reflect.Field field ->
                properties[field.name] = schemaForField(field)
                if (!NULLABLE_FIELDS.contains(field.name)) {
                    required << field.name
                }
            }

        [
            type: 'object',
            properties: properties,
            required: required
        ]
    }

    private static Map<String, Object> schemaForField(java.lang.reflect.Field field) {
        Map<String, Object> schema = schemaFor(field.genericType)

        if (NULLABLE_FIELDS.contains(field.name)) {
            schema.type = [schema.type, 'null']
        }

        schema
    }

    private static Map<String, Object> schemaFor(Type type) {
        if (type instanceof Class) {
            Class<?> clazz = (Class<?>) type

            if (clazz.isEnum()) {
                return schemaFor(clazz)
            }

            if (clazz == String) {
                return [type: 'string']
            }

            if (clazz == Integer || clazz == int) {
                return [type: 'integer']
            }

            return schemaFor(clazz)
        }

        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type
            Type rawType = parameterizedType.rawType

            if (rawType == List) {
                return [
                    type: 'array',
                    items: schemaFor(parameterizedType.actualTypeArguments[0])
                ]
            }
        }

        throw new IllegalArgumentException("Unsupported review response type: " + type.toString())
    }
}
