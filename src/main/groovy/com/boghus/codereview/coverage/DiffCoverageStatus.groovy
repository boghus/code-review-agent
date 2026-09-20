package com.boghus.codereview.coverage

import groovy.transform.CompileStatic

@CompileStatic
enum DiffCoverageStatus {
    COVERAGE_AVAILABLE,
    NO_EXECUTABLE_CHANGES,
    MAPPING_ERROR
}
