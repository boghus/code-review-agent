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

Los fixtures viven en **este mismo repositorio**, como archivos bajo `e2e/fixtures/<scenario>/`. El harness los aplica sobre ramas `qa/e2e-<scenario>` abiertas contra `main`, de modo que cada rama materializa un único cambio deliberado. `qa-merge-guard` garantiza que esas ramas nunca se mergeen.

Las ramas y sus pull requests son artefactos desechables: el harness las recrea en cada corrida a partir de los archivos de fixture y del ref bajo prueba. Por eso el fixture no depende de un pull request abierto y puede validarse contra un tag o un release candidate.

| Scenario | Fixture | Rama | Trigger inicial | Trigger adicional | Resultado esperado |
|---|---|---|---|---|---|
| `critical` | Cambio con un secreto/API key hardcodeado | `qa/e2e-critical` | `opened` | — | Comentario CRA con al menos un finding CRITICAL |
| `high` | Cambio con un bug funcional evidente | `qa/e2e-high` | `opened` | — | Comentario CRA con al menos un finding HIGH |
| `medium` | Cambio con duplicación o ausencia de test | `qa/e2e-medium` | `opened` | — | Comentario CRA con al menos un finding MEDIUM |
| `clean` | Cambio trivial y limpio | `qa/e2e-clean` | `opened` | — | Comentario CRA con 0 findings en las cuatro severidades |
| `idempotency` | Cambio trivial; luego commit vacío | `qa/e2e-idempotency` | `opened` | `synchronize` | Un solo comentario CRA; se actualiza conservando el ID |
| `provider-failure` | Modelo inválido (`invalid-provider-model`) | `qa/e2e-provider-failure` | `opened` | — | Comentario de error controlado, sin findings |

Las reglas usadas en cada review provienen de `.github/code_review_rules.md` en el **base ref** (`main`), porque `TrustedRulesLoader` resuelve las reglas desde el base ref del PR y nunca desde el diff.

El perfil `qa` de `Examples CI` ejecuta el self-review y omite build y tests, de modo que cada fixture paga únicamente el costo de la llamada al provider. El merge está bloqueado por `qa-merge-guard`.

**Sobre la severidad esperada:** un finding de severidad concreta no está garantizado por el prompt, solo por el contenido del fixture. Por eso el harness valida la severidad contra una severidad mínima esperada y clasifica un fallo como varianza del modelo cuando el reporte es válido y estructurado pero no incluye el finding esperado. Los fixtures CRITICAL/HIGH/MEDIUM deben materializarse antes de que el harness pueda ejecutarlos como escenarios deterministas.

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
- marker presente;
- un único comentario CRA.

La ausencia de `APPROVE` es intencional y no es un hueco del harness: el producto no emite un veredicto estructurado. Ver "Veredicto `APPROVE` / `CHANGES_REQUESTED`" más abajo.

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

**Fixture:** cambio trivial y deliberadamente limpio, en la rama `qa/e2e-clean`.

**Trigger:** `pull_request.opened`

**PASS:** el review se publica, conserva el marker, existe un único comentario CRA y reporta cero findings en las cuatro severidades (`CRITICAL 0 · HIGH 0 · MEDIUM 0 · LOW 0`).

**FAIL:** no existe comentario, aparecen findings contra el contrato del fixture o se publican múltiples comentarios CRA.

> 0 findings significa que el reviewer no reportó problemas bajo el contrato configurado; no significa que el código esté libre de bugs.

### `idempotency`

Comprueba que un PR tenga un solo comentario del Code Review Agent.

**Fixture:** cambio trivial en la rama `qa/e2e-idempotency`, usando las reglas de `.github/code_review_rules.md`.

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

**Fixture:** configuración deliberadamente inválida en la rama `qa/e2e-provider-failure`, siguiendo el caso histórico de `boghus/msp_energia` (PR #63), donde el modelo fue configurado como `invalid-provider-model`. El escenario se materializa en este repositorio para no depender de un PR externo.

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

El repositorio consumidor de los fixtures es **este repositorio**. Las ramas `qa/e2e-<scenario>` se abren contra `main` y las reglas aplicadas son las de `.github/code_review_rules.md` de ese base ref.

Las reglas utilizadas durante el review deben provenir del **base ref** del PR. Un cambio de reglas dentro del PR no debe modificar las reglas confiables usadas para ese mismo review.

Los fixtures CRITICAL/HIGH/MEDIUM son archivos versionados junto al harness, de modo que son reproducibles por definición. El harness no debe seleccionar automáticamente un cambio arbitrario para satisfacer una severidad.

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
- comportamiento ante error del provider;
- ausencia de secretos.

Los conteos de severidad son la assertion central porque provienen del renderer y no del modelo: `ReviewResponseMarkdownRenderer` emite la línea `**Severity summary:** 🔴 N CRITICAL · 🟠 N HIGH · 🟡 N MEDIUM · 🔵 N LOW` a partir de `findings.size()`, y emite `Found **N finding(s)**` con el mismo origen. El harness parsea esa línea a una tupla de enteros y compara enteros, nunca prosa.

### No deterministas

No se comparan literalmente:

- resumen generado por el LLM;
- explicación del impacto;
- recomendación;
- orden de frases;
- redacción de títulos sin contrato explícito;
- cualquier párrafo narrativo generado por el modelo.

### Veredicto `APPROVE` / `CHANGES_REQUESTED`

Estas cadenas **no son asercionables** y el harness no debe exigirlas.

El producto no emite un veredicto estructurado. No existe un campo `conclusion` en `ReviewResponse`, ni el renderer lo produce, y la Action publica un *comment* de issue en lugar de un *review* de pull request, así que tampoco existe un campo `state` de GitHub que leer.

La única fuente de esas palabras es `.github/code_review_rules.md`, que se le envía al modelo como instrucción ("Begin with a summary containing severity counts and an APPROVE / CHANGES_REQUESTED verdict"). Cuando aparecen en el reporte es porque el modelo las incluyó por voluntad propia dentro del campo `summary`, es decir dentro de texto no determinista.

Exigirlas convertiría un escenario en flaky sin aportar señal: el modelo puede redactar el veredicto de tres maneras distintas, o no incluirlo, sin que el comportamiento de la Action haya cambiado. Por eso `clean` se valida exclusivamente por cero findings en las cuatro severidades, y los escenarios con findings por severidad y cantidad.

Si en el futuro se requiere un veredicto, debe agregarse como campo de primer nivel en el contrato de review y renderizarse de forma determinista, y recién entonces pasa a ser asercionable.

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

El **E2E Test Harness (#128)** implementa estos contratos en `e2e/run-suite` y `e2e/lib/`. Consume el contrato sin tener que interpretar qué significa PASS o FAIL: las assertions de cada escenario están en `e2e/lib/assertions.py` y los escenarios en `e2e/lib/scenarios.py`.

La orquestación de release vive en `.github/workflows/release-candidate.yml`, que ejecuta la suite contra un ref concreto y sube los comentarios publicados como artifacts.

Este documento es el **contrato base permanente**: los releases siguientes deben reutilizar esta matriz como baseline, no como una suite exclusiva.
