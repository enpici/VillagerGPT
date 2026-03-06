# VillagerLife Troubleshooting (Citizens / WorldEdit)

Guía rápida para resolver fallos típicos de construcción en `VillagerLife`.

## Síntoma: no se crean NPCs o los agentes no navegan

**Logs típicos**
- `Citizens integration INACTIVE: plugin not installed, disabled, or API unavailable.`
- Agentes sin movimiento, tareas en `idle`/`wander` sin pathfinding real.

**Causa probable**
- Citizens no está instalado, está deshabilitado o no es compatible con tu versión de Paper.

**Qué revisar**
1. Que `plugins/Citizens*.jar` exista y cargue sin errores al arrancar.
2. `plugins/VillagerLife/config.yml` tenga `integration.citizens-enabled: true`.
3. Versión de Citizens compatible con tu versión de servidor.

**Acción recomendada**
- Instala/actualiza Citizens y reinicia. Si no lo vas a usar, deja la integración deshabilitada y asume comportamiento fallback de movimiento.

## Síntoma: build cancelado por blueprint no encontrado

**Logs típicos**
- `Build cancelado: blueprint no encontrado: <id>`
- Evento estructurado: `event=build_failed ... reason=blueprint_not_found`

**Causa probable**
- Falta el archivo `plugins/VillagerLife/blueprints/<id>.schem`.
- El id en comando no coincide (se normaliza a minúsculas).

**Acción recomendada**
- Verifica nombre exacto del schematic y vuelve a encolar con `/villagerlife build <blueprint>`.

## Síntoma: WorldEdit paste falla

**Logs típicos**
- `Falló paste WE para blueprint '<id>': <error>`
- Evento estructurado: `event=build_failed ... reason=worldedit_paste_failed`

**Causa probable**
- WorldEdit ausente/desactualizado.
- Mundo/chunk no cargado correctamente.
- Excepción interna al pegar schematic.

**Qué revisar**
1. Plugin WorldEdit cargado en startup (`softdepend`).
2. Integridad del `.schem` (abre y re-exporta si hace falta).
3. Coordenadas y mundo de destino válidos.

## Síntoma: materiales insuficientes

**Logs típicos**
- `Build cancelado: materiales insuficientes. Faltante={...}`
- Evento estructurado: `event=build_failed ... reason=insufficient_materials_or_sources`

**Causa probable**
- No hay inventarios/cofres en rango o falta stock.

**Acción recomendada**
1. Configura `build.material-sources` en `config.yml`.
2. Ajusta `build.nearby-container-radius`.
3. Asegura que el builder o cofres tengan materiales.

## Síntoma: error I/O schematic

**Logs típicos**
- `Error I/O leyendo schematic '<id>' intento X/Y: ...`
- Evento estructurado final: `event=build_failed ... reason=schematic_io_error`

**Causa probable**
- Archivo corrupto, permisos de lectura, o formato no soportado.

**Acción recomendada**
- Re-exporta schematic en formato compatible (`.schem`) y verifica permisos del archivo.

## Comando operativo para diagnóstico

- `/villagerlife buildstatus`
  - Muestra cola, progreso de builders y errores recientes.
  - Muestra contadores en memoria: éxitos, fallos, reintentos.
- `/villagerlife buildstatus reset`
  - Resetea contadores y errores recientes para una nueva ventana de observación.

## Campos de log estructurado

Cada fase emite:
- `event`: `build_started|build_step|build_failed|build_completed`
- `villageId`
- `agentUuid`
- `blueprintId`
- `stepIndex`
- `elapsedMs`
- `reason` (solo en fallos)

Esto permite filtrar en agregadores (`Loki`, `ELK`, `Datadog`) con queries por `event` y `reason`.
