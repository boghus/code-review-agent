# 🧪 E2E suite — contrato de releases

Este documento define el **contrato base de la suite E2E del Code Review Agent** para los releases actuales y futuros.

La suite debe reutilizar esta especificación como referencia común. Cada release puede agregar escenarios o endurecer assertions, pero no debe redefinir arbitrariamente los contratos existentes.

## Principios

1. Validar el comportamiento observable de la Action sobre un Pull Request real.
2. Usar assertions deterministas; no comparar literalmente texto generado por Gemini.
3. Validar estructura, severidad, cantidad de findings, marker, comentario y comportamiento entre ejecuciones.
4. Tratar los fallos del provider como errores controlados, no como findings.
5. Mantener el review AI como non-blocking.
6. Permitir que cada release agregue escenarios específicos sin romper el contrato base.

## Matriz base

| Scenario | Fixture | Trigger inicial | Trigger adicional | Resultado esperado |
|---|---|---|---|---|
| `critical` | Cambio intencional que produzca CRITICAL | `opened` | — | Comentario CRA con al menos un finding CRITICAL |
| `high` | Cambio intencional que produzca HIGH | `opened` | — | Comentario CRA con al menos un finding HIGH |
| `medium` | Cambio intencional que produzca MEDIUM | `opened` | — | Comentario CRA con al menos un finding MEDIUM |
| `clean` | Cambio sin findings bajo las reglas del fixture | `opened` | — | Comentario CRA con 0 findings |
| `idempotency` | PR con review CRA existente | `opened` | `synchronize` | Un solo comentario CRA; se actualiza |
| `provider-failure` | Provider/model inválido o fallo controlado | `opened` | — | Error controlado, sin findings inventados |

Estos escenarios forman el **mínimo contractual común**. Una versión concreta puede agregar escenarios adicionales cuando introduzca comportamiento nuevo que requiera validación E2E.

## Contrato común

Todos los escenarios deben comprobar:

- existe un comentario identificable por el marker `<!-- code-review-agent-by-boghus -->`;
- el comentario pertenece a `github-actions[bot]`;
- el reporte tiene la estructura soportada por la versión bajo prueba;
- el texto narrativo generado por Gemini no se compara literalmente.

Para escenarios con findings se valida:

- severidad esperada;
- cantidad mínima de findings;
- estructura del finding;
- archivo/línea cuando el fixture lo haga determinista;
- consistencia del resumen de severidades.

Para `clean` se valida:

- exactamente 0 findings;
- CRITICAL, HIGH, MEDIUM y LOW en cero;
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

**Fixture:** cambio pequeño y deliberadamente limpio respecto de las reglas configuradas.

**Trigger:** `pull_request.opened`

**PASS:** el review se publica, conserva el marker y reporta cero findings en todas las severidades.

**FAIL:** no existe comentario, aparecen findings contra el contrato del fixture o se publican múltiples comentarios CRA.

> 0 findings significa que el reviewer no reportó problemas bajo el contrato configurado; no significa que el código esté libre de bugs.

### `idempotency`

Comprueba que un PR tenga un solo comentario del Code Review Agent.

**Triggers:**

1. `pull_request.opened`
2. modificación del PR que genere `pull_request.synchronize`

`reopened` queda como trigger adicional a automatizar cuando el runner pueda producirlo de forma determinista.

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

Cubre un fallo del provider/modelo y la configuración inválida del provider cuando corresponda.

**Fixture:** configuración deliberadamente inválida o provider preparado para fallar.

**Trigger:** `pull_request.opened`

**Assertions:**

- el fallo se identifica como error de provider/configuración;
- no se inventan findings;
- el comportamiento de publicación corresponde al contrato de error;
- no se exponen API keys, tokens ni secretos.

**PASS:** el fallo se maneja de forma controlada y observable.

**FAIL:** el workflow queda bloqueado sin el comportamiento documentado, se publican findings ficticios o se exponen secretos.

## Reglas de los fixtures

Cada fixture debe declarar:

```text
scenario
fixture repository
rules file
expected severity
expected finding count/minimum
expected file/line, when deterministic
trigger
```

Las reglas utilizadas durante el review deben provenir del **base ref** del PR. Un cambio de reglas dentro del PR no debe modificar las reglas confiables usadas para ese mismo review.

## Assertions deterministas vs. AI

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

### No deterministas

No se comparan literalmente:

- resumen generado por Gemini;
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
4. las assertions no dependen de texto generado literalmente por Gemini;
5. un fallo de provider puede distinguirse de un review válido;
6. `idempotency` demuestra que el comentario CRA se actualiza en lugar de duplicarse.

## Relación con la implementación

Esta especificación define **qué debe demostrar la suite**, no dónde deben vivir sus runners o fixtures.

El PR #85 estableció la orquestación de release para `critical`, `high`, `medium`, `clean`, `idempotency` y `provider-failure`. Este documento formaliza el contrato base que debe cumplir cada escenario y que deberá reutilizarse en releases posteriores.
