package com.boghus.codereview.output

import com.boghus.codereview.review.Finding
import com.boghus.codereview.review.ReviewResult
import groovy.transform.CompileStatic

@CompileStatic
class MarkdownReviewFormatter {

    String format(ReviewResult result) {
        ReviewResult safeResult = result ?: new ReviewResult()

        if (safeResult.isEmpty()) {
            return cleanResult()
        }

        if (safeResult.hasCriticalOrHigh()) {
            return actionRequired(safeResult)
        }

        return recommendations(safeResult)
    }

    private static String cleanResult() {
        '''## 📊 Resultado del análisis

### ✅ Sin hallazgos

🔴 0 Critical · 🟠 0 High · 🟡 0 Medium · 🔵 0 Low

No detectamos problemas en los cambios analizados. 🚀

Tu PR está listo para revisión.
'''.stripIndent()
    }

    private static String recommendations(ReviewResult result) {
        String findings = result.findings.collect { Finding finding ->
            formatFinding(finding)
        }.join('\n\n---\n\n')

        return """## 📊 Resultado del análisis

### ⚠️ Hay puntos a revisar

🔴 ${result.count('CRITICAL')} Critical · 🟠 ${result.count('HIGH')} High · 🟡 ${result.count('MEDIUM')} Medium · 🔵 ${result.count('LOW')} Low

Encontramos ${result.findings.size()} punto${result.findings.size() == 1 ? '' : 's'} que vale la pena revisar antes de aprobar el PR.

### 💡 Recomendaciones

${findings}

### 📊 Total de hallazgos

🔴 CRITICAL: ${result.count('CRITICAL')}
🟠 HIGH: ${result.count('HIGH')}
🟡 MEDIUM: ${result.count('MEDIUM')}
🔵 LOW: ${result.count('LOW')}
""".stripIndent()
    }

    private static String actionRequired(ReviewResult result) {
        String findings = result.findings.collect { Finding finding ->
            formatFinding(finding)
        }.join('\n\n---\n\n')

        String actionItems = result.findings.findAll { Finding finding ->
            finding.severity.equalsIgnoreCase('CRITICAL') || finding.severity.equalsIgnoreCase('HIGH')
        }.collect { Finding finding ->
            "- [ ] Resolver el hallazgo ${finding.severity}"
        }.join('\n')

        return """## 🤖 Code Review

### ❌ Changes requested

**Resumen**

🔴 ${result.count('CRITICAL')} Critical · 🟠 ${result.count('HIGH')} High · 🟡 ${result.count('MEDIUM')} Medium · 🔵 ${result.count('LOW')} Low

### 🚨 Hallazgos

${findings}

### ⚠️ Nivel de riesgo

**Alto**

### ✅ Action items

${actionItems}

### 📊 Total de hallazgos

🔴 CRITICAL: ${result.count('CRITICAL')}
🟠 HIGH: ${result.count('HIGH')}
🟡 MEDIUM: ${result.count('MEDIUM')}
🔵 LOW: ${result.count('LOW')}
""".stripIndent()
    }

    private static String formatFinding(Finding finding) {
        String location = finding.lines?.trim() ? "📍 ${finding.file}:${finding.lines}" : "📍 ${finding.file}"
        String verification = finding.verified ? 'Verified' : 'Unverified'

        """### ${emoji(finding.severity)} ${finding.severity.capitalize()} — ${finding.title}

${location}

**Problema**
${finding.problem}

**Impacto**
${finding.impact}

💡 **Recomendación**
${finding.suggestedFix}

**Evidence:** ${finding.evidence}
**Verification:** ${verification}
""".stripIndent().trim()
    }

    private static String emoji(String severity) {
        switch (severity?.toUpperCase()) {
            case 'CRITICAL': return '🔴'
            case 'HIGH': return '🟠'
            case 'MEDIUM': return '🟡'
            default: return '🔵'
        }
    }
}
