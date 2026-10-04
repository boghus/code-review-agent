# 🧪 E2E suite — contrato de releases

Este documento define el **contrato base de la suite E2E del Code Review Agent** para los releases actuales y futuros.

La suite reutiliza esta especificación como referencia común. Cada release puede agregar escenarios o endurecer assertions, pero no debe redefinir arbitrariamente los contratos existentes.

## Principios

1. Validar el comportamiento observable de la Action sobre un Pull Request real.
2. Usar assertions deterministas; no comparar literalmente texto generado por el LLM.
3. Validar estructura, severidad, cantidad de findings, marker, comentario y comportamiento entre ejecuciones.
4. Tratar los fallos del provider como errores controlados, no como findings.
5. Mantener el review AI como non-blocking.
6. Permitir que cada release agregue escenarios específicos sin romper el contrato base.

## Matriz base

| Scenario | Fixture RC1 | Trigger inicial | Trigger adicional | Resultado esperado |
|---|---|---|---|---|
| `critical` | Fixture intencional CRITICAL sobre `boghus/msp_energia`; debe quedar fijado en un commit cuando el fixture se materialice | `opened` | — | Comentario CRA con al menos un finding CRITICAL |
| `high` | Fixture intencional HIGH sobre `boghus/msp_energia`; debe quedar fijado en un commit cuando el fixture se materialice | `opened` | — | Comentario CRA con al menos un finding HIGH |
| `medium` | Fixture intencional MEDIUM sobre `boghus/msp_energia`; debe quedar fijado en un commit cuando el fixture se materialice | `opened` | — | Comentario CRA con al menos un finding MEDIUM |
| `clean` | PR limpio sobre `boghus/msp_energia`; reglas: `.github/code_review_rules.md` | `opened` | — | Comentario CRA con 0 findings y `APPROVE` |
| `idempotency` | PR de prueba sobre `boghus/msp_energia`; reglas: `.github/code_review_rules.md` | `opened` | `synchronize` | Un solo comentario CRA; se actualiza conservando el ID |
| `provider-failure` | Configuración inválida del modelo/provider en `boghus/msp_energia` (caso histórico: PR #63) | `opened` | — | Comentario de error controlado, sin findings |

El consumidor RC1 previsto por la orquestación de release es `boghus/msp_energia`, mediante `.github/workflows/code-review-agent.yml`. El workflow acepta `opened`, `synchronize` y `reopened`.

**Nota sobre los fixtures de severidad:** no existe en el historial consultado un commit RC1 reproducible que permita afirmar honestamente un fixture específico para CRITICAL, HIGH y MEDIUM. No se inventan commits, archivos ni líneas. Esos tres fixtures deben materializarse antes de que el harness pueda ejecutarlos como escenarios deterministas.

Los escenarios forman el **mínimo contractual común**. Una versión concreta puede agregar escenarios adicionales cuando introduzca comportamiento nuevo que requiera validación E2E.

## Contrato común

Todos los escenarios de review válido deben comprobar:

- existe un comentario identificable por el marker `<!-- code-review-agent-by-boghus -->`;
- el comentario pertenece a `github-actions[bot]`;
- el reporte tiene la estructura soportada por la versión bajo prueba;
- el texto narrativo generado por el LLM no se compara literalmente.

El marker está integrado en el flujo productivo: `action.yml` lo utiliza para localizar el comentario existente y actualizarlo en lugar de crear uno nuevo.

Para escenarios con findings se valida:

- severidad esperada;
- cantidad mínima de findings;
- estructura del finding;
- archivo/línea cuando el fixture lo haga determinista;
- consistencia del resumen de severidades.

Para `clean` se valida:

- exactamente 0 findings;
- CRITICAL, HIGH, MEDIUM y LOW en cero;
- resultado `APPROVE`;
- marker presente.

## Escenarios base

### `critical`

**Fixture:** cambio deliberado que active una regla CRITICAL conocida del fixture.

**Trigger:** `pull_request.opened`

**PASS:**

- existe exactamente un comentario CRA;
- contiene al menos un finding CRITICAL;
- el marker aparece una vez.

**FAIL:** no existe comentario, no existe CRITICAL o se generan múltiples comentarios CRA.

### `high`

**Fixture:** cambio deliberado que active una regla HIGH conocida.

**Trigger:** `pull_request.opened`

**PASS:** existe exactamente un comentario CRA con al menos un finding HIGH y el marker aparece una vez.

**FAIL:** no existe el finding HIGH esperado o se duplica el comentario.

### `medium`

**Fixture:** cambio deliberado que active una regla MEDIUM conocida.

**Trigger:** `pull_request.opened`

**PASS:** existe exactamente un comentario CRA con al menos un finding MEDIUM y el marker aparece una vez.

**FAIL:** no existe el finding MEDIUM esperado o se duplica el comentario.

### `clean`

**Fixture:** cambio pequeño y deliberadamente limpio respecto de `.github/code_review_rules.md` en `boghus/msp_energia`.

**Trigger:** `pull_request.opened`

**PASS:** el review se publica, conserva el marker, reporta cero findings en todas las severidades y tiene resultado `APPROVE`.

**FAIL:** no existe comentario, aparecen findings contra el contrato del fixture, el resultado no es `APPROVE` o se publican múltiples comentarios CRA.

> 0 findings significa que el reviewer no reportó problemas bajo el contrato configurado; no significa que el código esté libre de bugs.

### `idempotency`

Comprueba que un PR tenga un solo comentario del Code Review Agent.

**Fixture:** PR de prueba en `boghus/msp_energia`, usando las reglas de `.github/code_review_rules.md`.

**Triggers:**

1. `pull_request.opened`
2. modificación del PR que genere `pull_request.synchronize`

`reopened` está soportado por el workflow consumidor, pero **no forma parte del baseline actual** porque todavía no existe una assertion adicional necesaria para justificarlo.

**Assertions después del primer trigger:**

- existe exactamente un comentario CRA;
- se captura su ID;
- existe el marker.

**Assertions después de `synchronize`:**

- sigue existiendo exactamente un comentario CRA;
- el ID es el mismo;
- el contenido corresponde a la segunda ejecución;
- no aparece un segundo comentario CRA.

**PASS:** el comentario existente se actualiza in place.

**FAIL:** se crea un segundo comentario, desaparece el anterior, cambia el ID sin razón o la segunda ejecución no actualiza el resultado.

### `provider-failure`

Cubre un fallo del provider/modelo y la configuración inválida del provider.

**Fixture:** configuración deliberadamente inválida. Existe un caso histórico reproducible en `boghus/msp_energia` (PR #63), donde el modelo fue configurado como `invalid-provider-model`.

**Trigger:** `pull_request.opened`

**Resultado observable contractual:**

- existe un comentario CRA;
- el comentario utiliza el marker;
- el comentario identifica que el provider no pudo completar la revisión;
- el review permanece non-blocking;
- no se publican findings ficticios;
- no se exponen API keys, tokens ni secretos.

**PASS:** el comentario de disponibilidad/error se publica y el workflow consumidor puede identificar el fallo como provider failure sin tratarlo como un finding.

**FAIL:** no existe comentario de error, se publican findings inventados, el workflow queda bloqueado por el fallo del provider o se exponen secretos.

## Reglas de los fixtures

Cada fixture debe declarar:

```text
scenario
fixture repository
fixture ref/commit
rules file
expected severity
expected finding count/minimum
expected file/line, when deterministic
trigger
expected publication state
```

Para RC1, el repositorio consumidor previsto es `boghus/msp_energia` y las reglas actuales están en `.github/code_review_rules.md`.

Las reglas utilizadas durante el review deben provenir del **base ref** del PR. Un cambio de reglas dentro del PR no debe modificar las reglas confiables usadas para ese mismo review.

Los fixtures CRITICAL/HIGH/MEDIUM requieren un commit fijo antes de considerarse completamente reproducibles. El harness no debe seleccionar automáticamente un cambio arbitrario para satisfacer una severidad.

## Assertions deterministas vs. LLM

### Deterministas

Sí forman parte del PASS/FAIL:

- existencia del comentario;
- marker;
- autor;
- cantidad de comentarios CRA;
- ID del comentario en idempotencia;
- severidades;
- cantidad de findings;
- estructura del reporte;
- archivo/línea cuando sea determinista;
- resultado `APPROVE` o `CHANGES_REQUESTED` cuando forme parte del escenario;
- comportamiento ante error del provider;
- ausencia de secretos.

### No deterministas

No se comparan literalmente:

- resumen generado por el LLM;
- explicación del impacto;
- recomendación;
- orden de frases;
- redacción de títulos sin contrato explícito;
- cualquier párrafo narrativo generado por el modelo.

## Evolución por release

Este documento define el **contrato base permanente** de la suite E2E.

Para cada release:

1. se ejecutan los escenarios base;
2. se agregan escenarios cuando el release introduce comportamiento nuevo;
3. se mantienen las assertions de los escenarios existentes salvo que exista un cambio de contrato explícito;
4. cualquier cambio incompatible debe documentarse como cambio de contrato y actualizar la estrategia E2E correspondiente;
5. los fixtures y runners pueden evolucionar independientemente mientras sigan cumpliendo este contrato.

Por ejemplo, `v1.0.0` utiliza la matriz base como parte de su validación de release, pero las siguientes versiones deben reutilizar esta misma matriz como **baseline**, no como una suite exclusiva de `v1.0.0`.

## Criterio global de PASS

La suite es reproducible cuando:

1. cada escenario puede ejecutarse desde un fixture conocido;
2. el trigger está definido;
3. el resultado esperado está expresado como assertions observables;
4. las assertions no dependen de texto generado literalmente por el LLM;
5. un fallo de provider puede distinguirse de un review válido;
6. `idempotency` demuestra que el comentario CRA se actualiza en lugar de duplicarse.

## Relación con la implementación

Esta especificación define **qué debe demostrar la suite**, no dónde deben vivir sus runners o fixtures.

El PR #85 estableció la orquestación de release para `critical`, `high`, `medium`, `clean`, `idempotency` y `provider-failure`. Este documento formaliza el contrato base que debe cumplir cada escenario y que deberá reutilizarse en releases posteriores.

El siguiente paso de implementación es el **E2E Test Harness (#128)**, que debe consumir estos contratos sin tener que interpretar qué significa PASS o FAIL.
