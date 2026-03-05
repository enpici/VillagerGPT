# Viabilidad de migración a Paper 1.21.11

## TL;DR

Sí, **la migración es viable**, pero ahora mismo no es “subir versión y listo”.

Resultado del spike:
- `paper-api:1.19` -> `1.21.11-R0.1-SNAPSHOT`: compila con ajustes puntuales.
- `plugin.yml api-version`: debe pasar de `1.19` a `1.21`.
- La suite de test actual falla al migrar porque depende de MockBukkit/registro de tags y cambios de API de Paper 1.21.

Conclusión realista: **riesgo medio**. Bloqueante principal: capa de testing, no la lógica de negocio del plugin.

---

## Qué validé

1. Verifiqué disponibilidad de Paper 1.21.11 en el repositorio oficial Maven (sí existe).
2. Simulé upgrade de dependencias de build:
   - `compileOnly/testImplementation` Paper API -> `1.21.11-R0.1-SNAPSHOT`.
   - MockBukkit -> variante `v1.21`.
3. Ejecuté `./gradlew test` para medir impacto real.

---

## Hallazgos técnicos

### 1) API de Paper

Hay cambios menores que rompen compilación en código legado 1.19 (ejemplo: acceso a nombres de biome/profession en Kotlin por cambios de firma/obsolescencia).

Impacto: **bajo**, son ajustes mecánicos.

### 2) Tests con MockBukkit

Aparecen fallos en tests al correr con stack 1.21:
- `InternalTagMisconfigurationException` en tests de comandos.
- errores de inicialización/clases en tests de `TradeOfferProcessor`.

Impacto: **medio-alto** para CI. El plugin puede estar bien, pero sin reparar tests no tienes red de seguridad.

### 3) Runtime esperado

No se observan dependencias NMS ni hacks version-specific en el código principal, por lo que el riesgo en runtime del plugin es menor que el riesgo en test harness.

---

## Riesgo por área

- **Compilación plugin**: Bajo
- **Tests automáticos**: Medio-Alto
- **Compatibilidad runtime en Paper real**: Medio
- **Esfuerzo total estimado**: 0.5–1.5 días (según la guerra con MockBukkit)

---

## Plan recomendado (pragmático)

1. Crear rama de migración dedicada (`chore/paper-1.21.11`).
2. Subir Paper API + `api-version` en `plugin.yml`.
3. Resolver cambios de compilación Kotlin/API (trivial).
4. Actualizar/ajustar tests que dependan de internals de tags/trades.
5. Añadir smoke tests manuales en servidor Paper 1.21.11 real:
   - comandos `ttv`, `ttvclear`, `ttvend`
   - inicio/cierre conversación
   - oferta de trades
6. Dejar CI en verde y recién ahí merge.

---

## Recomendación final

**Sí migraría a 1.21.11**, pero en dos pasos:

- Paso 1: compatibilidad de build + runtime.
- Paso 2: estabilización de tests.

Intentar cerrar todo en un único commit “rápido” es la receta clásica para romper CI y comerte rollback.
