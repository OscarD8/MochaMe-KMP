# MochaMe Module Dependency Flow Analysis

This document provides a comprehensive map of the inter-project dependency flows among the modules of **MochaMe**. It details how dependencies are set up, highlights central dependency injection from `:build-logic`, maps reachability, and visualizes the architecture using a Mermaid diagram.

---

## 1. Executive Summary & Design Insights

* **Isolated Core Contracts**: The `:core:contract` module is completely isolated, having no dependencies on any other module in the project. Per the central build configuration, almost all modules depend on it via an `api` (transitive) dependency.
* **Decoupled Sync Engine**: The `:sync-engine` does **not** rely on any `:mocha` feature modules, remaining a self-contained system. It depends only on `:core` and `:system` modules.
* **Sequential Feature Cascade**: The `:mocha:mocha-feature` modules are structured in a strict sequential chain:
  $$\text{:mocha:mocha-feature:resonance} \longrightarrow \text{:mocha:mocha-feature:telemetry} \longrightarrow \text{:mocha:mocha-feature:bio} \longrightarrow \text{:sync-engine} / \text{:core:platform} / \text{:core:contract}$$
* **Standalone Build Logic**: The `:build-logic` project is an *included build* (`includeBuild`) that contains shared convention plugins. It defines the targets, test runners, and standard dependencies (like injecting `:core:contract` and `:core:logger` automatically).
* **Ignored Modules** (per instructions): `:app` (and its submodules), `:mocha:mocha-ui`, and `:mocha:mocha-schema`.

---

## 2. Centralized Dependency Injection (`:build-logic`)

Shared dependencies are governed by the custom convention plugins inside `build-logic`. Specifically, [TargetConfig.kt](file:///home/oscarmichael/AndroidStudioProjects/MochaMe/build-logic/src/main/kotlin/com/mochame/gradle/TargetConfig.kt#L89-L108) and [TestConfig.kt](file:///home/oscarmichael/AndroidStudioProjects/MochaMe/build-logic/src/main/kotlin/com/mochame/gradle/TestConfig.kt#L10-L28) define standard dependencies that are applied transitively based on the convention plugin used:

### A. Main Dependencies Injection (`applyStandardDependencies`)
Applied to all modules using `mocha.convention.provider`, `mocha.convention.logic`, or `mocha.convention.feature`:
1. **Core Contract**: Every module except `:core:contract` itself gets `api(project(":core:contract"))`. This exposes the contracts transitively to any downstream consumer.
2. **Core Logger**: Every module except `:core:contract` and `:core:logger` gets `implementation(project(":core:logger"))`. This is an internal dependency.
3. **Test Support (for Fixtures)**: Any test fixtures module (name starting with `:core:test:fixtures-`) gets `api(project(":core:test:support"))`.

### B. Test Dependencies Injection (`configureTestTargets`)
Applied to all modules with test builders enabled (modules using `mocha.convention.logic` or `mocha.convention.feature`):
1. **Common Test Support**: In `commonTest`, these modules automatically receive `implementation(project(":core:test:support"))`.

---

<details>
<summary><b> Module Dependency Table </b></summary>

<br>

This table summarizes direct project-level dependencies for all analyzed modules.

| Module Path | Applied Convention Plugin | Direct Main Dependencies (API) | Direct Main Dependencies (Implementation) | Direct Test Dependencies (`commonTest` / `jvmTest`) | Downstream Dependents (What depends on this module) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`:core:contract`** | `mocha.convention.provider` | None | None | None | **All modules** (except itself) |
| **`:core:logger`** | `mocha.convention.provider` | `:core:contract` *(implicit)* | None | None | **All modules** (except `:core:contract` & itself) |
| **`:core:sync-contract`** | `mocha.convention.provider` | `:core:contract` *(implicit)* | `:core:logger` *(implicit)* | None | `:system:infra`, `:sync-engine`, `:mocha:mocha-feature:bio` (and downstream features) |
| **`:core:utils`** | `mocha.convention.logic` | `:core:contract` *(implicit)* | `:core:logger` *(implicit)* | `:core:test:support` *(implicit)* | `:core:platform`, `:system:orchestrator`, `:sync-engine`, `:core:test:fixtures-contract`, `:core:test:fixtures-utils` |
| **`:core:platform`** | `mocha.convention.logic` | `:core:contract` *(implicit)* | `:core:utils`, `:core:logger` *(implicit)* | `:core:test:support` *(implicit)* | `:core:test:support`, `:sync-engine`, `:mocha:mocha-feature:bio`, `:core:test:fixtures-platform`, `:system:infra` *(test)* |
| **`:core:test:test-logger`** | `mocha.convention.provider` | `:core:contract` *(implicit)*, `:core:logger` | None | None | `:core:test:support` |
| **`:core:test:support`** | `mocha.convention.provider` | `:core:contract` *(implicit)*, `:core:test:test-logger` | `:core:platform`, `:core:logger` *(implicit)* | None | All fixtures (Main API); All logic/feature modules (Test) |
| **`:core:test:fixtures-contract`** | `mocha.convention.provider` | `:core:contract`, `:core:test:support` *(implicit)* | `:core:utils`, `:core:logger` *(implicit)* | None | `:system:orchestrator` *(test)*, `:sync-engine` *(test)* |
| **`:core:test:fixtures-system-infra`** | `mocha.convention.provider` | `:core:contract` *(implicit)*, `:core:test:support` *(implicit)* | `:core:logger` *(implicit)* | None | `:sync-engine` *(test)* |
| **`:core:test:fixtures-utils`** | `mocha.convention.provider` | `:core:utils`, `:core:contract` *(implicit)*, `:core:test:support` *(implicit)* | `:core:logger` *(implicit)* | None | `:sync-engine` *(test)* |
| **`:core:test:fixtures-platform`** | `mocha.convention.provider` | `:core:platform`, `:core:contract` *(implicit)*, `:core:test:support` *(implicit)* | `:core:logger` *(implicit)* | None | `:sync-engine` *(test)* |
| **`:system:infra`** | `mocha.convention.feature` | `:core:contract` *(implicit)* | `:core:sync-contract` *(implicit)*, `:core:logger` *(implicit)* | `:core:platform`, `:core:test:support` *(implicit)* | `:system:orchestrator`, `:sync-engine` |
| **`:system:orchestrator`** | `mocha.convention.logic` | `:core:contract` *(implicit)* | `:core:utils`, `:system:infra`, `:core:logger` *(implicit)* | `:core:test:fixtures-contract`, `:core:test:support` *(implicit)* | None *(except ignored app modules)* |
| **`:sync-engine`** | `mocha.convention.feature` | `:core:contract` *(implicit)* | `:core:platform`, `:core:utils`, `:system:infra`, `:core:sync-contract` *(implicit)*, `:core:logger` *(implicit)* | `:core:test:fixtures-contract`, `:core:test:fixtures-system-infra`, `:core:test:fixtures-utils`, `:core:test:fixtures-platform`, `:core:test:support` *(implicit)* | `:mocha:mocha-feature:bio` |
| **`:mocha:mocha-feature:bio`** | `mocha.convention.feature` | `:core:contract` *(implicit)* | `:core:contract`, `:sync-engine`, `:core:platform`, `:core:sync-contract` *(implicit)*, `:core:logger` *(implicit)* | `:core:test:support` *(implicit)* | `:mocha:mocha-feature:telemetry` |
| **`:mocha:mocha-feature:telemetry`** | `mocha.convention.feature` | `:core:contract` *(implicit)* | `:mocha:mocha-feature:bio`, `:core:sync-contract` *(implicit)*, `:core:logger` *(implicit)* | `:core:test:support` *(implicit)* | `:mocha:mocha-feature:resonance` |
| **`:mocha:mocha-feature:resonance`** | `mocha.convention.feature` | `:core:contract` *(implicit)* | `:mocha:mocha-feature:telemetry`, `:core:sync-contract` *(implicit)*, `:core:logger` *(implicit)* | `:core:test:support` *(implicit)* | None *(except ignored app modules)* |

> [!NOTE]
> Implicit dependencies are injected by the convention plugins inside `:build-logic`. Transitive `api` dependencies from a library are automatically exposed to any module that depends on it.

</details>

---

<details>
<summary><b> Reachability and Navigation </b></summary>

<br>

This map defines what modules you can reach (import classes from) depending on where you are situated in the codebase, and what modules can transitively reach you.

```
[Module Name]
├── CAN REACH (Inside the module, what is compile-visible to you)
│   ├── Main: Direct & transitive API dependencies
│   └── Test: Additional test-scope dependencies
└── CAN BE REACHED BY (Downstream consumers)
    ├── Main: Modules that depend on you
    └── Test: Modules that depend on you for tests
```

### :core:contract
* **CAN REACH**:
  * Main: *None (Isolated)*
  * Test: *None*
* **CAN BE REACHED BY**:
  * Main: Every single module in the codebase (via standard build logic injection).

### :core:logger
* **CAN REACH**:
  * Main: `:core:contract`
* **CAN BE REACHED BY**:
  * Main: Every single module except `:core:contract` (via standard build logic injection).

### :core:sync-contract
* **CAN REACH**:
  * Main: `:core:contract` (via direct `api` dependency, exposed transitively) and `:core:logger` (via direct `implementation` dependency, internal-only).
* **CAN BE REACHED BY**:
  * Main: `:system:infra`, `:sync-engine`, `:mocha:mocha-feature:bio`, `:mocha:mocha-feature:telemetry`, `:mocha:mocha-feature:resonance`.

### :core:utils
* **CAN REACH**:
  * Main: `:core:contract`, `:core:logger` *(internal)*
  * Test: `:core:test:support` *(internal)*
* **CAN BE REACHED BY**:
  * Main: `:core:platform`, `:system:orchestrator`, `:sync-engine`
  * Test: `:core:test:fixtures-contract`, `:core:test:fixtures-utils`

### :core:platform
* **CAN REACH**:
  * Main: `:core:utils` *(internal)*, `:core:contract`, `:core:logger` *(internal)*
  * Test: `:core:test:support` *(internal)*
* **CAN BE REACHED BY**:
  * Main: `:core:test:support`, `:sync-engine`, `:mocha:mocha-feature:bio`
  * Test: `:system:infra` *(test)*, `:core:test:fixtures-platform` *(test fixture)*

### :core:test:test-logger
* **CAN REACH**:
  * Main: `:core:logger`, `:core:contract`
* **CAN BE REACHED BY**:
  * Main: `:core:test:support` (transitive to all fixtures)

### :core:test:support
* **CAN REACH**:
  * Main: `:core:platform` *(internal)*, `:core:test:test-logger`, `:core:contract`, `:core:logger` *(internal)*
* **CAN BE REACHED BY**:
  * Main: `:core:test:fixtures-contract`, `:core:test:fixtures-system-infra`, `:core:test:fixtures-utils`, `:core:test:fixtures-platform`
  * Test: `:core:utils`, `:core:platform`, `:system:infra`, `:system:orchestrator`, `:sync-engine`, `:mocha:mocha-feature:bio`, `:mocha:mocha-feature:telemetry`, `:mocha:mocha-feature:resonance`

### :core:test:fixtures-contract
* **CAN REACH**:
  * Main: `:core:contract`, `:core:test:support`, `:core:utils` *(internal)*, `:core:logger` *(internal)*, `:core:test:test-logger` *(transitive)*
* **CAN BE REACHED BY**:
  * Test: `:system:orchestrator` *(test)*, `:sync-engine` *(test)*

### :core:test:fixtures-system-infra
* **CAN REACH**:
  * Main: `:core:contract`, `:core:test:support`, `:core:logger` *(internal)*, `:core:test:test-logger` *(transitive)*
* **CAN BE REACHED BY**:
  * Test: `:sync-engine` *(test)*

### :core:test:fixtures-utils
* **CAN REACH**:
  * Main: `:core:utils`, `:core:contract`, `:core:test:support`, `:core:logger` *(internal)*, `:core:test:test-logger` *(transitive)*
* **CAN BE REACHED BY**:
  * Test: `:sync-engine` *(test)*

### :core:test:fixtures-platform
* **CAN REACH**:
  * Main: `:core:platform`, `:core:contract`, `:core:test:support`, `:core:logger` *(internal)*, `:core:test:test-logger` *(transitive)*
* **CAN BE REACHED BY**:
  * Test: `:sync-engine` *(test)*

### :system:infra
* **CAN REACH**:
  * Main: `:core:contract`, `:core:sync-contract` *(internal)*, `:core:logger` *(internal)*
  * Test: `:core:platform` *(internal)*, `:core:test:support` *(internal)*
* **CAN BE REACHED BY**:
  * Main: `:system:orchestrator`, `:sync-engine`

### :system:orchestrator
* **CAN REACH**:
  * Main: `:core:utils` *(internal)*, `:system:infra` *(internal)*, `:core:contract`, `:core:logger` *(internal)*
  * Test: `:core:test:fixtures-contract` *(internal)*, `:core:test:support` *(internal)*
* **CAN BE REACHED BY**:
  * None *(top-level system component)*

### :sync-engine
* **CAN REACH**:
  * Main: `:core:platform` *(internal)*, `:core:utils` *(internal)*, `:system:infra` *(internal)*, `:core:contract`, `:core:logger` *(internal)*, `:core:sync-contract` *(internal)*
  * Test: `:core:test:fixtures-contract` *(internal)*, `:core:test:fixtures-system-infra` *(internal)*, `:core:test:fixtures-utils` *(internal)*, `:core:test:fixtures-platform` *(internal)*, `:core:test:support` *(internal)*
* **CAN BE REACHED BY**:
  * Main: `:mocha:mocha-feature:bio`

### :mocha:mocha-feature:bio
* **CAN REACH**:
  * Main: `:sync-engine` *(internal)*, `:core:platform` *(internal)*, `:core:contract`, `:core:logger` *(internal)*, `:core:sync-contract` *(internal)*
  * Test: `:core:test:support` *(internal)*
* **CAN BE REACHED BY**:
  * Main: `:mocha:mocha-feature:telemetry`

### :mocha:mocha-feature:telemetry
* **CAN REACH**:
  * Main: `:mocha:mocha-feature:bio` *(internal)*, `:core:contract`, `:core:logger` *(internal)*, `:core:sync-contract` *(internal)*, `:sync-engine` *(transitive)*, `:core:platform` *(transitive)*
  * Test: `:core:test:support` *(internal)*
* **CAN BE REACHED BY**:
  * Main: `:mocha:mocha-feature:resonance`

### :mocha:mocha-feature:resonance
* **CAN REACH**:
  * Main: `:mocha:mocha-feature:telemetry` *(internal)*, `:core:contract`, `:core:logger` *(internal)*, `:core:sync-contract` *(internal)*, `:mocha:mocha-feature:bio` *(transitive)*, `:sync-engine` *(transitive)*, `:core:platform` *(transitive)*
  * Test: `:core:test:support` *(internal)*
* **CAN BE REACHED BY**:
  * None *(top-level feature component)*

</details>

---

<details>
<summary><b> Visual Flowchart (Mermaid) </b></summary>

<br>

<img src="/docs/images/project_dependency_flow.webp" alt="project dependency flow mermaid diagram">

<br>

### Mermaid Code:

```mermaid
---
config:
  look: handDrawn
  theme: dark
  layout: elk
  elk:
    nodePlacementStrategy: BRANDES_KOEPF
    edgeRouting: ORTHOGONAL
    spacing.nodeNodeBetweenLayers: 100
---
flowchart BT
    %% --- LAYER SUBGRAPHS ---

    subgraph BL [Build Logic]
        build_logic[":build-logic"]
    end

    subgraph Core [Core Foundation Layer]
        contract[":core:contract"]
        logger[":core:logger"]
        sync_contract[":core:sync-contract"]
        utils[":core:utils"]
        platform[":core:platform"]
    end

    subgraph TestBase [Test Support Infrastructure]
        test_logger[":core:test:test-logger"]
        test_support[":core:test:support"]
    end

    subgraph Fixtures [Test Fixtures]
        fix_contract[":core:test:fixtures-contract"]
        fix_platform[":core:test:fixtures-platform"]
        fix_utils[":core:test:fixtures-utils"]
        fix_infra[":core:test:fixtures-infra"]
    end

    subgraph System [System Layer]
        sys_infra[":system:infra"]
        sys_orch[":system:orchestrator"]
    end

    subgraph Sync [Sync Engine]
        sync_engine[":sync-engine"]
    end

    subgraph Features [Mocha Feature Layer]
        feat_bio[":mocha:mocha-feature:bio"]
        feat_tel[":mocha:mocha-feature:telemetry"]
        feat_res[":mocha:mocha-feature:resonance"]
    end

    %% --- PATH RELATIONSHIPS ---

    %% [BL] Build Logic Injections (0 to 3)
    build_logic -.-> contract
    build_logic -.-> logger
    build_logic -.-> utils
    build_logic -.-> platform

    %% [Core] Foundation Paths (4 to 11)
    logger --> contract
    sync_contract --> contract
    sync_contract --> logger
    utils --> contract
    utils --> logger
    platform --> utils
    platform --> contract
    platform --> logger

    %% [TestBase] Infrastructure Paths (12 to 17)
    test_logger --> logger
    test_logger --> contract
    test_support --> platform
    test_support --> test_logger
    test_support --> contract
    test_support --> logger

    %% [Fixtures] Test Fixture Paths (18 to 28)
    fix_contract --> contract
    fix_contract --> utils
    fix_contract --> test_support
    fix_platform --> platform
    fix_platform --> contract
    fix_platform --> test_support
    fix_utils --> utils
    fix_utils --> contract
    fix_utils --> test_support
    fix_infra --> contract
    fix_infra --> test_support

    %% [System] Layer Paths (29 to 39)
    sys_infra --> contract
    sys_infra --> logger
    sys_infra --> sync_contract
    sys_infra --> platform
    sys_infra -.-> test_support

    sys_orch --> utils
    sys_orch --> sys_infra
    sys_orch --> contract
    sys_orch --> logger
    sys_orch -.-> fix_contract
    sys_orch -.-> test_support

    %% [Sync] Engine Paths (40 to 50)
    sync_engine --> platform
    sync_engine --> utils
    sync_engine --> sys_infra
    sync_engine --> contract
    sync_engine --> logger
    sync_engine --> sync_contract
    sync_engine -.-> fix_contract
    sync_engine -.-> fix_infra
    sync_engine -.-> fix_utils
    sync_engine -.-> fix_platform
    sync_engine -.-> test_support

    %% [Features] Module Inter-Dependencies (51 to 66)
    feat_bio --> contract
    feat_bio --> sync_engine
    feat_bio --> platform
    feat_bio --> sync_contract
    feat_bio --> logger
    feat_bio -.-> test_support

    feat_tel --> feat_bio
    feat_tel --> contract
    feat_tel --> logger
    feat_tel --> sync_contract
    feat_tel -.-> test_support

    feat_res --> feat_tel
    feat_res --> contract
    feat_res --> logger
    feat_res --> sync_contract
    feat_res -.-> test_support

    %% --- DESIGN & THEME PALETTE ---

    classDef blStyle fill:none,stroke:#cbd5e1,stroke-width:2px,color:#f8fafc
    classDef coreStyle fill:none,stroke:#38bdf8,stroke-width:2px,color:#e0f2fe
    classDef testStyle fill:none,stroke:#475569,stroke-width:2px,color:#94a3b8
    classDef fixStyle fill:none,stroke:#a855f7,stroke-width:2px,color:#d8b4fe
    classDef sysStyle fill:none,stroke:#f59e0b,stroke-width:2px,color:#fcd34d
    classDef syncStyle fill:none,stroke:#ef4444,stroke-width:3px,color:#fecaca
    classDef featStyle fill:none,stroke:#10b981,stroke-width:2px,color:#ecfdf5

    class build_logic blStyle
    class contract,logger,sync_contract,utils,platform coreStyle
    class test_logger,test_support testStyle
    class fix_contract,fix_platform,fix_utils,fix_infra fixStyle
    class sys_infra,sys_orch sysStyle
    class sync_engine syncStyle
    class feat_bio,feat_tel,feat_res featStyle

    style BL fill:none,stroke:#cbd5e1,stroke-dasharray: 5 5,color:#cbd5e1
    style Core fill:none,stroke:#38bdf8,stroke-dasharray: 5 5,color:#38bdf8
    style TestBase fill:none,stroke:#475569,stroke-dasharray: 5 5,color:#475569
    style Fixtures fill:none,stroke:#a855f7,stroke-dasharray: 5 5,color:#a855f7
    style System fill:none,stroke:#f59e0b,stroke-dasharray: 5 5,color:#f59e0b
    style Sync fill:none,stroke:#ef4444,stroke-dasharray: 5 5,color:#ef4444
    style Features fill:none,stroke:#10b981,stroke-dasharray: 5 5,color:#10b981

    %% --- STRICT LINK-COLOR MATRIX DIRECTIVES ---

    %% 1. From Build Logic (Slate White)
    linkStyle 0,1,2,3 stroke:#cbd5e1,stroke-width:1px,stroke-dasharray: 3

    %% 2. From Core Layer (Sky Blue)
    linkStyle 4,5,6,7,8,9,10,11 stroke:#38bdf8,stroke-width:2px

    %% 3. From Test Infrastructure Base (Ghost Slate)
    linkStyle 12,13,14,15,16,17 stroke:#475569,stroke-width:2px

    %% 4. From Test Fixtures (Royal Violet)
    linkStyle 18,19,20,21,22,23,24,25,26,27,28 stroke:#a855f7,stroke-width:2px

    %% 5. From System Layer (Amber Production Rows)
    linkStyle 29,30,31,32,34,35,36,37 stroke:#f59e0b,stroke-width:2px

    %% 6. From Sync Engine Core (Crimson Production Rows)
    linkStyle 40,41,42,43,44,45 stroke:#ef4444,stroke-width:2px

    %% 7. From Feature Modules (Emerald Production Rows)
    linkStyle 51,52,53,54,55,57,58,59,60,62,63,64,65 stroke:#10b981,stroke-width:2px

    %% 8. ISOLATED TEST DEPENDENCIES (Universal Royal Violet Lock)
    linkStyle 33,38,39,46,47,48,49,50,56,61,66 stroke:#a855f7,stroke-width:2px,stroke-dasharray: 4
```

</details>


---
*Generated by Antigravity on 2026-06-15.*
