# Migración a Maven multi-módulo (Villager Suite)

## 1) Análisis conceptual de la estructura actual

Estado previo detectado:

- Proyecto plugin único (VillagerGPT) con código Kotlin y build Gradle/Maven híbrido.
- Contratos de integración (`VillagerContext`, `VillagerContextProvider`, `VillagerGPTService`) mezclados dentro del mismo plugin.
- Un solo artefacto empaqueta API + implementación, lo que complica:
  - evolución independiente de VillagerLife,
  - testing de integración entre plugins,
  - estabilidad contractual.

Conclusión: hay que separar **API estable** y **plugins concretos** sin romper Paper Services API.

## 2) Propuesta de estructura Maven

Se adopta una arquitectura multi-módulo minimalista:

- `villager-api`: contratos compartidos (interfaces + DTOs ligeros).
- `villagergpt-plugin`: implementación de diálogo y narrativa.
- `villagerlife-plugin`: implementación de simulación y proveedor de contexto.

`villager-common` **no se crea** aún: no hay evidencia clara de utilidades realmente compartidas y estables.

## 3) Árbol del proyecto

```text
villager-suite-parent/
├── pom.xml
├── README.md
├── docs/
│   ├── maven-multi-module-migration.md
│   ├── paper-server-deploy.md
│   └── viabilidad-paper-1.21.11.md
├── villager-api/
│   ├── pom.xml
│   └── src/main/kotlin/tj/horner/villagergpt/api/
│       ├── VillagerContext.kt
│       ├── VillagerContextProvider.kt
│       └── VillagerGPTService.kt
├── villagergpt-plugin/
│   ├── pom.xml
│   └── src/...
└── villagerlife-plugin/
    ├── pom.xml
    └── src/
        └── main/
            ├── java/io/github/enpici/villager/life/VillagerLifePlugin.java
            └── resources/plugin.yml
```

## 4) POM del parent

Responsabilidades del parent:

- versionado global del suite
- Java 21 y encoding
- `dependencyManagement`
- `pluginManagement`
- definición de módulos

(Ver `pom.xml` en raíz.)

## 5) POM de cada módulo

- `villager-api/pom.xml`: dependencia mínima de Paper API (provided) + Kotlin stdlib.
- `villagergpt-plugin/pom.xml`: depende de `villager-api` + deps de IA/memoria + shade para artefacto final del plugin.
- `villagerlife-plugin/pom.xml`: depende de `villager-api` + Paper API; sin shade por ahora.

## 6) Reorganización sugerida de paquetes Java/Kotlin

### Estado transitorio (aplicado)

- API extraída a módulo `villager-api` manteniendo namespace legacy (`io.github.enpici.villager.api`) para minimizar ruptura.

### Estado objetivo (recomendado)

Migrar progresivamente a:

- `io.github.enpici.villager.api`
- `io.github.enpici.villager.gpt`
- `io.github.enpici.villager.life`

Estrategia recomendada:

1. duplicar contratos API en nuevo namespace,
2. marcar legacy como `@Deprecated`,
3. retirar legacy en siguiente major.

## 7) Plan de migración paso a paso

1. **Freeze funcional** del plugin actual (sin features nuevas durante split).
2. Crear parent multi-módulo y mover fuentes de plugin a `villagergpt-plugin`.
3. Extraer contratos a `villager-api`.
4. Ajustar `VillagerGPTService` para no filtrar tipos internos del plugin (acción genérica API).
5. Crear bootstrap mínimo de `villagerlife-plugin` con `VillagerContextProvider` registrado en `ServicesManager`.
6. Adaptar pipeline CI para build multi-módulo y artefactos por plugin.
7. Ejecutar pruebas de regresión del módulo `villagergpt-plugin`.
8. (Siguiente iteración) migrar namespaces a `io.github.enpici.*`.

## 8) Recomendaciones de versionado

- Versionar todo el suite desde parent (`2.0.0-SNAPSHOT` actual).
- Mantener compatibilidad semántica:
  - cambios API incompatibles => major,
  - extensiones compatibles => minor,
  - fixes => patch.
- Publicar `villager-api` como artefacto independiente para integradores.

## 9) Explicación de dependencias (sin ciclos)

Grafo permitido y aplicado:

```text
villager-api
   ↑       ↑
   |       |
villagergpt-plugin   villagerlife-plugin
```

No existe dependencia directa entre plugins.
Integración en runtime: Bukkit events + `ServicesManager` + contratos de `villager-api`.

## 10) Plan específico de migración desde Gradle

1. Mantener Maven como build canónico.
2. Retirar wrappers/scripts Gradle y su configuración para evitar doble fuente de verdad.
3. Portar repositorios/deps/plugins relevantes a POMs.
4. Actualizar CI para solo Maven.
5. Validar output JAR por módulo en `target/`.

## 11) Estabilidad de API (recomendaciones)

- API pequeña: interfaces + DTOs ligeros + eventos compartidos.
- Evitar exponer tipos internos de implementación del plugin GPT.
- Añadir tests de compatibilidad binaria (Japicmp/Revapi) cuando se publique `villager-api`.
- Política de deprecación mínima: 1 minor antes de retirar.

## 12) CI/CD recomendado

Pipeline mínimo:

1. `mvn -B clean verify`
2. matriz de pruebas por módulo crítico
3. publicación de artefactos (`villagergpt-plugin`, `villagerlife-plugin`)
4. (release) deploy de `villager-api` a repositorio Maven

Checks útiles:

- Enforcer plugin (Java/Maven versions)
- dependencyConvergence
- reproducible builds

## 13) Dónde usar shading y dónde no

- **Usar shade en `villagergpt-plugin`** (ya configurado), porque incluye libs runtime (OpenAI, Ktor, SQLite, etc.).
- **No usar shade en `villager-api`**: debe ser liviano y sin empaquetado gordo.
- **No usar shade en `villagerlife-plugin` por defecto**: activarlo solo si incorpora dependencias runtime no provistas por Paper o el servidor.

