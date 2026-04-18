# MochaMe-KMP

This project is a proof-of-concept exploration into Kotlin Multiplatform. The goal is to build an isolated, privacy-centric, local-first sync engine deployable across platforms with minimal platform boilerplate, with the application incorporating edge AI inference. The centerpiece of this architecture is platform testability - utilizing Gradle and Koin.

---
<details>
<summary> <b> Design Goals </b> </summary>

#### Learning and Exposure
The main focus for learning is to understand if/how Kotlin Multiplatform & Compose Multiplatform can be leveraged in its current state to achieve parity across platforms using the same core logic - in the following: 
- concurrency
- sync conflict resolution
- recovery
- atomicity for local persistence and synced intents across platforms
- platform automated routines
- application deployment per target
- local inference
- encoding
- encryption 


#### Choice of Tech Stack
The intent is to build a dry, modular architecture that achieves all of the above utilizing Kotlin's strengths that I have come across: generics, flows and state handling, coroutines, inline functions, c interops (at a very very high level), the bundled SQLite driver (capability of Room KMP in general), nullability handling - as well as Gradle build tools alongside Koin, and the host of KMP and CMP tools - to create a component based architecture isolated by design, platform agnostic where possible, achieving platform parity with minimal boilerplate, and completely testable by component. I also want to explore different ways of working with AI through this process.


#### Schema / AI Inference / Privacy
'MochaMe' acts as a relatively simple schema that primarily enables exposure to all of the above. The data model is designed to allow for logging and tracking of learning and experiences, as well as books read and any saved quotes (...something I want). A qualitative sleep metric wraps each 'daily context' and its 'moments' (see data model below). I hope to link up the generated data to an inference layer later in development that uses platform swappable edge LLMs to generate predictive/descriptive analysis for insight into how different 'moments' and their own context, with trends over time, affect personal energy, learning, mood, satisfaction, and consequential moments/decisions (this will also allow for performance comparisons of models across device specs). 

Its a contained attempt to guage how the current state of local artificial intelligence models can become an extension of our own ability to analyze our internal states and decision making. To achieve any level of actual insight, the data must be high fidelity (though its currently purely qualitative). Privacy is also central as the application handles personal data and lifestyle habits with openness. Through a local-first architecture and local inference models, I hope to achieve complete privacy as the server only holds encypted binary payloads with sync metadata as a log entry. These payloads mean nothing at the server level. The local device itself is the only location where the payloads can be interpreted (a master-key sharable by QR code will provide a distributed system the same interpretation). That data is then not fed to a cloud LLM. 


#### TLDR
Ultimately, this is an attempt to gauge how a local-first architecture with edge AI can provide a private extension of our own self-analysis and reflection. It is a proof of concept for personal analytics that avoids trading our data for insight.

</details>

---

<details>
<summary><b>Local First Architecture</b></summary>

<br>

<img width="550" height="700" alt="image" src="https://github.com/user-attachments/assets/01e432c0-9afb-4f34-bb44-c33a3d0dda27" />


</details>

---

<details>
<summary><b> Multiplatform Architecture </b></summary>

#### Approach to Development:

```
MochaMe/
├── testing/                          # PROVIDER: Instrumentation
│   └── :mocha-test-support           # Platform-agnostic mocks & DB builders
│
├── core-platform/                    # PROVIDER: Infrastructure
│   └── src/                          # Expect/Actual (Hasher, Identity, POSIX)
│       ├── commonMain/
│       └── [platform]Main/           # jvm, linuxX64, android, ios
│       └── [platform]Test/           # jvm, linuxX64, android, ios

│
├── sync-engine/                      # TIER 0: SYNC PROTOCOL
│   └── src/
│       ├── commonMain/
│       └── commonTest/
│
├── mocha-feature/                    # TIER 1: DOMAIN (Pure Kotlin)
│   └── :bio / :telemetry / :resonance
│       └── src/
│           ├── commonMain/
│           └── commonTest/           # Fast testing via LinuxX64 engine
│
├── mocha-ui/                         # TIER 2: UI
│   └── src/
│       ├── commonMain/               # ComposeMultiplatform
│       └── [platform]Main/           # Native resources (Insets, Windowing)
│
└── platform-*/                       # TIER 3: Entry Point
    ├── :androidApp/                   # Android APK + @Database
    ├── :desktopAppJVM/                   # JVM/Desktop + @Database
    ├── :iosApp/                       # iOS Framework + @Database
    └── :cli-linux/                    # (Headless) Native Binary + @Database
```
<br>

<div align="center">

<img width="700" height="700" alt="kmp-arch" src="https://github.com/user-attachments/assets/ca4561e8-9010-4be4-a8a5-91cf05117001" />

</div>


</details>

---

<details>
<summary><b> AI Approach for Development </b></summary>

#### AI Usage Aim:

Different contexts assigned roles attempting to achieve domain specialization and cross verification. 
Each shift to developing a new component of the system requires a refresh to the context, and specific documentation + code files relevant to that component. 
Initially a cross team brainstorm is performed, defining a specification, which is then used to develop code which is verified against the Go/No-Go check across domains. 
This development system was designed to introduce me to new concepts, cope with AI hallucinations, and make a significant amount of the development cycle not about implementation but critical analysis.
It was an experiment to see if AI can be used to achieve cross-domain critical analysis in solo development. 
Inspired by NASA's launch of Artemis II (and that I kept having to refactor) :)

| Lead    | Domain       | Focus & Technical Details                     |
| :------ | :----------- | :-------------------------------------------- |
| **SSL** | Architecture | Decoupling, DI (Koin), and Module Boundaries. |
| **CCL** | Concurrency  | Coroutines, Mutexes, and HLC Causality.       |
| **DPL** | Persistence  | Room KMP, SQLite Atomicity, and Migrations.   |
| **STL** | Safety       | Exception Mapping, Boot State, and Forensics. |
| **GSL** | Local First  | Causality, server operations, and conflicts.  |

#### Example prompt:

ROLE: Concurrency Control Lead (CCL)

Timeline: 2026 Standard Operating Procedure
Objective: Verification of thread-safety, causal consistency (HLC), and non-blocking asynchronous orchestration.

1. THE CONTEXT (NASA FLIGHT RULES)

        You are the Concurrency Control Lead (CCL). You manage the "Timing and Sequencing" of this rocket.
        Your mission is to eliminate race conditions, deadlocks, and "Zombie Tasks" before they reach the pad. You operate under a Launch Commit Criteria (LCC) framework. If a Coroutine scope isn't properly bounded or a Mutex is shadowed, you issue an "Abort" command. You work alongside the SSL, DPL, and STL. 2. THE 2026 TECH STACK (ACTIVE CONFIGURATION)

All concurrency audits must be strictly compliant with:

    Language: Kotlin 2.3.10 (K2) with strict Coroutine testing.

    Asynchronous Core: kotlinx-coroutines 1.10.2.

    Time Tracking: kotlinx-datetime 0.7.1 and Hybrid Logical Clock (HLC) implementations.

    Persistence Interaction: Room 2.8.4 KMP with SQLITE_BUSY awareness.

    Testing: kotlinx-coroutines-test and Turbine 1.2.1 for Flow verification.

    Targets: Android (SDK 36), Linux (Desktop), with future iOS/Native compatibility

3. YOUR CORE CONCERNS (CCL CONSOLE)

Expect to verify other teams code against these concerns:

    Causal Integrity: Verify that HLC timestamps remain monotonic and mutations are ordered correctly.

    Mutex Orchestration: Prevent "Mutex Shadowing" where a Kotlin lock prevents the system from reaching the Database resilience layer.

    Thread Confinement: Ensure no IO-heavy tasks leak onto the Main/UI thread.

    DRY code and Kotlin syntax sugar: Consider the usage of interfaces, abstraction, and kotlin best practices for 2026. Consider helper functions where necessary, and ensuring code is dry and that readability is a priority over technicality.

    Resource Leaks: Audit Coroutine Scopes to ensure tasks are cancelled when a component or lifecycle is destroyed.


4. THE MISSION PROCEDURE (LCC CHECKLIST)

For every proposal, you must provide a Concurrency Go/No-Go status based on:

    [ ] Atomicity: Is the operation wrapped in a proper Mutex or Transaction boundary?

    [ ] Non-Blocking: Does this use withContext(dispatcherProvider.io) for all blocking calls?

    [ ] Causality: Does the implementation preserve HLC order for local-first sync?

    [ ] Resilience: Does the delay() logic use proper jitter to prevent phase-locking?

5. THE ANKI PROTOCOL

Every major implementation discussion must conclude with a flashcard:

    Concept: [Name of the Pattern/Concept]

    Component: [The specific API or Code Block]

    Problem/Question: [The failure state this solves]

    Breakdown: [Bullet points explaining the 'Why']

    Code/Analogy: [A lean code snippet or a grounded analogy]

    Gradle 10.0 Warning: [Specific configuration or versioning trap]

5. INTEGRATION NOTES

Extra details:

    HLC Cardiologist: [The CCL should be the most skeptical of "System Time." It must always assume the clock is skewed or jumping.]

    Turbine 1.2.1 Mastery: [In 2026, the CCL should suggest using Turbine for any Flow verification to ensure all emissions are captured before a test finishes.]

    The "Wait" Strategy: [The CCL should be the one to suggest withTimeout() or withTimeoutOrNull() on any boot-path operation to prevent infinite hangs.]


7.  PHASE 0: PRE-FLIGHT BRAINSTORM (PDR/CDR)

Before generating any implementation code, you must enter a "Design-First" state. Your initial output for a new component must be a Structural Specification, not a code solution.
You are strictly forbidden from writing functional code until a Go/No-Go Poll has been completed across all consoles. During this phase, you must:

        Identify Critical Constraints: Define the specific architectural boundaries for this component.

        Map Potential Failure Modes (FMEA): Predict where this component will likely hit different exception types or a Race Condition.

        Request External Validation: If your design depends on a decision from another console (e.g., CCL for locking or DPL for schema), you must explicitly pause and ask Launch Control to "Poll the [Console Lead]" for confirmation.

        Define Launch Commit Criteria (LCC): List the 3 specific conditions that must be true for this code to be considered "safe to launch."

8.  [DAILY INPUT BLOCK]

Launch Control (User) to CCL:
Current Task: [INPUT COMPONENT OR PROBLEM HERE]
Target Files/Documentation: [LIST ATTACHED FILES HERE]

CCL, provide your initial FMEA (Failure Mode and Effects Analysis) and Concurrency Audit.

</details>

---

<details>
<summary><b> Data Model </b></summary>

```mermaid
classDiagram
    %% --- BIO MODULE (The Context) ---
    class DailyContext {
        <<Entity>>
        +String id PK "UUID"
        +Long epochDay "Unique Index"
        +Double sleepHours
        +Int readinessScore
        +Bool isNapped "The Outlier Flag"
        +Long lastModified "Sync Timestamp"
    }

    %% --- TELEMETRY MODULE (The Work) ---
    class Domain {
        <<Entity>>
        +String id PK "UUID"
        +String name
        +String hexColor
        +String iconKey
        +Boolean isActive
        +Long lastModified "Sync Timestamp"
    }

    class Topic {
        <<Entity>>
        +String id PK "UUID"
        +String domainId FK "UUID"
        +String name
        +Boolean isActive
        +Long lastModified "Sync Timestamp"
    }

    class Moment {
        <<Entity>>
        +String id PK "UUID"
        +String domainId FK "fka categoryId"
        +String? topicId FK
        +String? spaceId FK
        +Int satisfactionScore "1-10"
        +Int mood
        +Int energyDelta "-5 to +5"
        +Int intensityScale "1-10"
        +Int? entryEnergy "1-10"
        +String? note
        +Boolean? isFocusTime
        +Int? socialScale "fka isSocial"
        +Int? biophiliaScale "1-5"
        +Int? durationMinutes
        +Boolean? isDaylight
        +Int? cloudDensity
        +Boolean? isPrecipitating
        +Long timestamp "ms"
        +Long associatedEpochDay "4am Rule"
        +Long lastModified "Sync Timestamp"
    }

    class Space {
        <<Entity>>
        +String id PK "UUID"
        +String name "Unique"
        +String iconKey
        +Int? defaultBiophilia "1-5"
        +Boolean isControlled "Private vs Public"
        +Boolean isActive
        +Long lastModified
    }

    %% --- SIGNAL MODULE (The Archive) ---
    class Author {
        <<Entity>>
        +String id PK "UUID"
        +String name
        +Boolean isActive
        +Long lastModified "Sync Timestamp"
    }

    class Book {
        <<Entity>>
        +String id PK "UUID"
        +String authorId FK "UUID"
        +String title
        +Long dateAdded
        +Long dateFinished
        +Boolean isActive
        +Long lastModified "Sync Timestamp"
    }

    class Quote {
        <<Entity>>
        +String id PK "UUID"
        +String bookId FK "UUID"
        +String content
        +Resonance resonance
        +Int viewCount
        +Long lastModified "Sync Timestamp"
    }

    class Resonance {
        <<Enumeration>>
        WONDER
        LOGIC
        SADNESS
        JOY
    }

    class Mood {
        <<Enumeration (PAD MODEL)>>
        FOCUS
        WONDER
        ENERGIZED
        CALM
        NEUTRAL
        BORED
        TIRED
        SAD
        FRUSTRATED
        +pleasure: Int
        +energy: Int
        +agency: Int
        +fromName(name: String) Mood$
    }
    
    class SyncTombstoneEntity {
        +String entityId <<PK>>
        +String tableName
        +Long deletedAt
    }

    %% --- RELATIONSHIPS ---
    Moment "*" --> "1" Space : occurs in
    Domain "1" -- "*" Topic : contains
    Domain "1" -- "*" Moment : anchors
    Topic "0..1" -- "*" Moment : refines
    DailyContext .. Moment : Analytic Link (Join on epochDay)
    Author "1" -- "*" Book : writes
    Book "1" -- "*" Quote : contains
    Quote ..> Resonance : matches
    Moment ..> Mood : holds
```

</details>

---

<details>
<summary><b> Testing Architecture & Commands </b></summary>

<br>
The architecture is unified through a custom Gradle verification system that provides synchronized logging and automated cache invalidation across platforms.

### Testing Architecture

| Tier                   | Target              | Technology                     | Description                                                                                   |
|:-----------------------|:--------------------|:-------------------------------|:----------------------------------------------------------------------------------------------|
| **Common**             | `commonTest`        | `kotlin.test`, Turbine, MockK  | Platform-agnostic logic, ViewModels, and Flow/Coroutine verification.                         |
| **JVM**                | `jvmTest`           | JUnit 5                        | High-speed desktop-side execution for shared logic and Desktop-specific components.           |
| **Host (Robolectric)** | `androidHostTest`   | Robolectric, JUnit 4 (Vintage) | Simulated Android environment running on the JVM. Includes SQLite/Room database verification. |
| **Instrumented**       | `androidDeviceTest` | AndroidJUnitRunner             | Hardware-accurate tests running on physical devices or emulators for UI and integration.      |

---

### Verification Commands

The project includes specialized Gradle tasks to manage the build lifecycle and testing
suites effectively. 'All' commands automatically bypass the task cache to ensure a fresh "
rerun" of the test logic.
Application is not implementing ios testing, but the setup allows integration in the
future.

The commands below print all tests results and their corresponding platform to the
terminal, providing
a cross-platform test run/analysis with a single command.

#### Local Suite (Fastest)

Use these for rapid iteration during active development.

- **`./gradlew verifyLocal`**: Runs all JVM and Android Host (Robolectric) tests.
- **`./gradlew verifyLocalAll`**: Performs a `clean` followed by all local unit tests to
  ensure no stale artifacts remain.

#### Full Suite (Comprehensive)

For pre-merge verification and final system checks.

- **`./gradlew verify`**: JVM, androidHost, and connected Android device tests.
- **`./gradlew verifyAll`**: The "Nuclear Option." Wipes the entire build directory and
  executes every test suite from scratch.

---

### Abstract Base Test Pattern

**Shared Logic (commonTest)**:

- Defines an abstract class containing all test scenarios and common logic using
  kotlin.test.
- E.g. declares an abstract fun createDatabase() to decouple logic from implementation.

**Platform Implementation (jvmTest, androidHostTest, etc.**):

- Each target extends the base class and provides their own concrete implementations
  handling their own dependencies. The commands above then run the platform instances.

</details>

---
