package com.boghus.codereview.review

import groovy.transform.CompileStatic

/**
 * Structured contract for a review finding.
 *
 * The model still returns the final review as markdown for backwards
 * compatibility, but the prompt uses this contract to make evidence and
 * verification explicit when producing each finding.
 */
@CompileStatic
class Finding {

    static final String PROMPT_CONTRACT = '''For each finding, use these fields:
- severity: CRITICAL, HIGH, MEDIUM, or LOW
- title: short title
- file: changed file path
- lines: number or range when known
- problem: what is wrong
- impact: why it matters
- suggestedFix: concrete remediation
- evidence: the code, test, configuration, or verified external evidence supporting the finding
- verified: true only when the finding is directly supported by the supplied code/context or by explicitly verified external evidence

Evidence rules:
- Keep a finding when there is a relevant signal, even if the evidence cannot be fully verified.
- If a finding depends on external or changing information that was not verified, set verified to false and use uncertainty language. Do not present that external claim as a fact.
- If there is no meaningful evidence or signal, do not report the finding.
- Never invent evidence or sources.'''
}