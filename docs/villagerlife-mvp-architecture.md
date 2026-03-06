# VillagerLife MVP Architecture (Paper + Java 21)

## 1) Árbol de paquetes recomendado

```text
io.github.enpici.villager.life
├── VillagerLifePlugin
├── agent
│   ├── Agent
│   ├── AgentManager
│   └── NeedType
├── ai
│   └── DecisionEngine
├── blueprint
│   └── BlueprintService
├── command
│   └── VillagerLifeCommand
├── event
│   ├── AgentRoleChangedEvent
│   ├── AgentThreatDetectedEvent
│   ├── VillagerBornEvent
│   ├── VillageFoodLowEvent
│   └── VillageStructureBuiltEvent
├── integration
│   └── VillagerLifeContextProvider
├── role
│   └── AgentRole
├── scheduler
│   └── SimulationScheduler
├── task
│   ├── BaseTask
│   ├── Task
│   ├── TaskStatus
│   └── impl
│       ├── MoveToTask
│       ├── HarvestTask
│       ├── DepositItemsTask
│       ├── EatTask
│       ├── SleepTask
│       ├── FleeTask
│       ├── WanderTask
│       └── BuildStructureTask
└── village
    ├── VillageAI
    └── VillageManager
```

## 2) Arquitectura general

- `VillagerLifePlugin` inicializa dependencias y registra `VillagerContextProvider` en ServicesManager.
- `AgentManager` mantiene el registro de agentes (UUID villager -> Agent).
- `VillageManager` mantiene la aldea activa (`VillageAI`) para el MVP.
- `SimulationScheduler` separa 3 loops:
  - ejecución de tarea (ligera)
  - decisión de agentes
  - planificación de aldea
- `DecisionEngine` aplica Utility score simple (sin LLM por tick).
- `BlueprintService` encapsula carga de `.schem` e integración futura con WorldEdit/FAWE.
- `VillagerLifeContextProvider` desacopla integración con VillagerGPT vía `villager-api`.

## 3) Clases principales y responsabilidad

- `Agent`: estado interno del aldeano, necesidades, rol, tarea activa, memoria mínima.
- `AgentManager`: alta/baja/búsqueda de agentes.
- `VillageAI`: stock, camas, límites de población, cola de blueprints, reglas de reproducción.
- `DecisionEngine`: selecciona tarea a ejecutar según score.
- `Task` + `BaseTask`: contrato extensible para tareas por pasos.
- `VillagerLifeCommand`: comandos de administración para testear simulación.

## 4) Flujo de simulación

1. `/villagerlife createvillage` crea `VillageAI` y spawnea 2 agentes iniciales.
2. Cada 20 ticks, agentes degradan necesidades y seleccionan tarea.
3. Cada tick, la tarea activa ejecuta `tick()` y finaliza con SUCCESS/FAILED/TIMEOUT.
4. Cada 200 ticks, `VillageAI` reevalúa estado global y emite eventos de colonia.
5. VillagerGPT puede consultar contexto vivo desde `VillagerLifeContextProvider`.

## 5) Modelo de datos base (in-memory MVP)

- Agente:
  - `villagerUuid`
  - `role`
  - `needs` (`HUNGER`, `ENERGY`, `SAFETY`, `SOCIAL`, 0-100)
  - `activeTask`
  - `lastEvent`
  - `relationshipsWithPlayers`
- Aldea:
  - `id`, `name`, `center`
  - `foodStock`, `bedCount`
  - `populationTarget`, `maxPopulation`
  - `lastThreatTick`, `lastReproductionTick`
  - `pendingBlueprints`

## 6) Estrategia de scheduling

- Task tick: cada 1 tick (muy ligera).
- Decision tick: cada 20 ticks.
- Village planning: cada 200 ticks.

## 7) Integración con villager-api

- Implementado `VillagerLifeContextProvider`.
- Expone: UUID, nombre, profesión, rol, hambre, energía, nombre de aldea, población, stock comida, último evento, relaciones.
- Registro vía ServicesManager con prioridad `High`.

## 8) Entidades y enums iniciales

- `NeedType`
- `AgentRole`
- `TaskStatus`
- `Task`
- `Agent`
- `VillageAI`

## 9) Plan por fases

1. **MVP actual**: agentes + necesidades + tareas + planner + comando + contexto.
2. **Persistencia**: repositorios SQLite para agentes/aldea/cola de build.
3. **Construcción real**: pegar `.schem` con WorldEdit/FAWE + validación espacio/materiales.
4. **Reproducción avanzada**: parejas, cooldown por pareja, señales de amenaza históricas.
5. **Optimizaciones**: sharding por chunks, caches de POI/bed/chests, balanceo por lotes.

## 10) Estado de código base

- Plugin compila y arranca con comando administrativo.
- Arquitectura preparada para extensión sin acoplarse a implementación interna de VillagerGPT.
- Eventos definidos para interoperabilidad con otros plugins.
