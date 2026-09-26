package com.boghus.codereview.output

import com.boghus.codereview.review.Finding
import com.boghus.codereview.review.ReviewResult
import groovy.transform.CompileStatic

@CompileStatic
class MarkdownReviewFormatter {

    String format(ReviewResult result) {
        ReviewResult safeResult = result ?: new ReviewResult([], false)

        if (!safeResult.valid) {
            return invalidResult()
        }
        if (safeResult.isEmpty()) {
            return cleanResult()
        }
        if (safeResult.hasCriticalOrHigh()) {
            return actionRequired(safeResult)
        }
        return recommendations(safeResult)
    }

    private static String invalidResult() {
        '''## 🤖 Code Review

### ⚠️ No se pudo interpretar el resultado

La respuesta del reviewer no tuvo el formato esperado. No podemos afirmar que no existan hallazgos.

Revisa el log del workflow y vuelve a ejecutar el review.
'''.stripIndent()
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
        }.join('\\n\\n---\\n\\n')

        return """## 📊 Resultado del análisis

### ⚠️ Hay puntos a revisar

🔴 ${result.count('CRITICAL')} Critical · 🟠 ${result.count('HIGH')} High · 🟡 ${result.count('MEDIUM')} Medium · 🔵 ${result.count('LOW')} Low

Encontramos ${result.findings.size()} punto${result.findings.size() == 1 ? '' : 's'} que vale la pena revisar antes de aprobar el PR.

### 💡 Recomendaciones

${findings}

${totals(result)}
""".stripIndent()
    }

    private static String actionRequired(ReviewResult result) {
        String findings = result.findings.collect { Finding finding ->
            formatFinding(finding)
        }.join('\\n\\n---\\n\\n')

        List<String> actionItems = []
        int criticalCount = result.count('CRITICAL')
        int highCount = result.count('HIGH')

        if (criticalCount > 0) {
            actionItems << "- [ ] Resolver los ${criticalCount} hallazgo${criticalCount == 1 ? '' : 's'} Critical"
        }
        if (highCount > 0) {
            actionItems << "- [ ] Resolver ${highCount} hallazgo${highCount == 1 ? '' : 's'} High"
        }

        result.findings.findAll { Finding finding ->
            finding.severity.equalsIgnoreCase('CRITICAL') || finding.severity.equalsIgnoreCase('HIGH')
        }.each { Finding finding ->
            String action = finding.suggestedFix?.trim() ?: finding.title
            actionItems << "- [ ] ${action}"
        }

        actionItems << '- [ ] Revisar las recomendaciones'

        return """## 🤖 Code Review

### ❌ Changes requested

**Resumen**

🔴 ${result.count('CRITICAL')} Critical · 🟠 ${result.count('HIGH')} High · 🟡 ${result.count('MEDIUM')} Medium · 🔵 ${result.count('LOW')} Low

### 🚨 Hallazgos

${findings}

### ⚠️ Nivel de riesgo

**Alto**

### ✅ Action items

${actionItems.join('\\n')}

${totals(result)}
""".stripIndent()
    }

    private static String totals(ReviewResult result) {
        """### 📊 Total de hallazgos

🔴 CRITICAL: ${result.count('CRITICAL')}
🟠 HIGH: ${result.count('HIGH')}
🟡 MEDIUM: ${result.count('MEDIUM')}
🔵 LOW: ${result.count('LOW')}""".stripIndent()
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