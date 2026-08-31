# MochaMe Logger Architecture Sweep & Runtime Log Output Analysis

## 1. Executive Summary

This document provides a comprehensive sweep of the **Kermit** and **Logger** architecture in **MochaMe-KMP**, alongside the **Gradle build system**. It explains why running:

```bash
./gradlew clean :app:entry:jvmApp:run
```

produces **no log output** in the terminal, whereas running unit tests (e.g., `./gradlew :core:platform:jvmTest` or `./gradlew test`) outputs fully formatted, colorized logs in the format of [CleanLogWriter](file:///home/nova/StudioProjects/MochaMe-KMP/core/logger/src/commonMain/kotlin/com/mochame/logger/CleanLogWriter.kt#L7-L52).

Finally, this document provides concrete, actionable recommendations for achieving seamless runtime logging with [CleanLogWriter](file:///home/nova/StudioProjects/MochaMe-KMP/core/logger/src/commonMain/kotlin/com/mochame/logger/CleanLogWriter.kt#L7-L52) on **Pop!_OS COSMIC**.

---

## 2. Logger Architecture Overview

The logging pipeline in MochaMe is split across three primary tiers:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Logger Modules Hierarchy                        │
├────────────────────────────────┬───────────────────────────────────────┤
│ Core Module                    │ Purpose                               │
├────────────────────────────────┼───────────────────────────────────────┤
│ :core:logger                   │ Production Kermit configuration,      │
│                                │ CleanLogWriter, PlatformTagModule,    │
│                                │ LogTags structure & Logger.withTags() │
├────────────────────────────────┼───────────────────────────────────────┤
│ :core:test:test-logger         │ Test Kermit configuration,            │
│                                │ TestLogWriter + CleanLogWriter combo  │
├────────────────────────────────┼───────────────────────────────────────┤
│ :build-logic                   │ Central Gradle testLogging &          │
│                                │ Koin compiler configuration           │
└────────────────────────────────┴───────────────────────────────────────┘
```

### A. The Production Logger Setup (`:core:logger`)

1. **[CleanLogWriter.kt](file:///home/nova/StudioProjects/MochaMe-KMP/core/logger/src/commonMain/kotlin/com/mochame/logger/CleanLogWriter.kt#L7-L52)**:
   * Extends Kermit's `LogWriter()`.
   * Formats messages into the standard structure:
     $$\text{[HH:mm:ss.SSS]} \;\boldsymbol{\rangle}\; \text{[SeverityChar]} \;\boldsymbol{\rangle}\; \text{[Tag]} \;\boldsymbol{\rangle}\; \text{Message}$$
   * Applies ANSI 24-bit TrueColor and 8-color escape sequences:
     * `Severity.Verbose` $\rightarrow$ ANSI 24-bit Blue (`\u001B[38;2;105;135;175m`)
     * `Severity.Debug` $\rightarrow$ ANSI 24-bit Warm Gray (`\u001B[38;2;138;125;113m`)
     * `Severity.Info` $\rightarrow$ ANSI Green (`\u001B[32m`)
     * `Severity.Warn` $\rightarrow$ ANSI Yellow (`\u001B[33m`)
     * `Severity.Error` $\rightarrow$ ANSI Red (`\u001B[31m`)
     * `Severity.Assert` $\rightarrow$ ANSI Magenta (`\u001B[35m`)
   * Emits formatted strings directly to `System.out` via Kotlin's `println()`.

2. **[LoggerModule.kt](file:///home/nova/StudioProjects/MochaMe-KMP/core/logger/src/commonMain/kotlin/com/mochame/logger/LoggerModule.kt#L13-L24)**:
   * Declares a Koin module that includes `PlatformTagModule`.
   * Binds a singleton `Logger` instance:
     ```kotlin
     @Single
     fun getLogger(@PlatformTag platformTag: String): Logger = Logger(
         config = StaticConfig(
             minSeverity = Severity.Verbose,
             logWriterList = listOf(CleanLogWriter(minSeverity = Severity.Verbose))
         ),
         tag = platformTag
     )
     ```
   * On JVM ([JvmLoggerUtils.kt](file:///home/nova/StudioProjects/MochaMe-KMP/core/logger/src/jvmMain/kotlin/com/mochame/logger/JvmLoggerUtils.kt#L8-L12)), `providePlatformTag()` supplies `"JVM"`.

3. **[LoggerUtils.kt](file:///home/nova/StudioProjects/MochaMe-KMP/core/logger/src/commonMain/kotlin/com/mochame/logger/LoggerUtils.kt#L6-L49)**:
   * Defines standard taxonomies for `LogTags.Domain` (`Meta`, `Node`, `Sync`, `Bio`, `Plat`, etc.) and `LogTags.Layer` (`UI..`, `Repo`, `Domn`, `Data`, `Infr`, `Orch`, etc.).
   * Provides `Logger.withTags(layer, domain, className)` to create tagged sub-loggers.

---

## 3. Why Tests Show Full Log Output

When running a test task (e.g. `./gradlew :core:platform:jvmTest`), log outputs are immediately displayed on the terminal:

```
BufferProviderTest[jvm] > should_returnDistinctInstances_when_calledFromDifferentThreads[jvm] STANDARD_OUT
    14:46:49.198 ❯ V ❯ [JVMTest ❯ Infr ❯ Plat ❯ Buffer] ❯ BUFFER | REUSE | Thread: DefaultDispatcher-worker-1 @coroutine#3 (ID: 40)
HasherProviderTest[jvm] > should_produceExactSha256_when_inputIsEmpty[jvm] STANDARD_OUT
    14:46:49.246 ❯ V ❯ [JVMTest ❯ Infr ❯ Plat ❯ SHA-256] ❯ Updated digest with 0 bytes
    14:46:49.247 ❯ D ❯ [JVMTest ❯ Infr ❯ Plat ❯ SHA-256] ❯ Digest finalized | Hash Size: 32 bytes
```

This occurs due to the intersection of two configurations:

1. **Gradle Test Runner Stream Forwarding**:
   In [build-logic/src/main/kotlin/com/mochame/gradle/ProjectExtensions.kt](file:///home/nova/StudioProjects/MochaMe-KMP/build-logic/src/main/kotlin/com/mochame/gradle/ProjectExtensions.kt#L27-L34):
   ```kotlin
   tasks.withType<AbstractTestTask>().configureEach {
       testLogging {
           outputs.upToDateWhen { false }
           showStandardStreams = true
           showExceptions = false
           events(TestLogEvent.FAILED)
       }
   }
   ```
   The setting `showStandardStreams = true` explicitly instructs Gradle to intercept `System.out` / `System.err` of the test worker JVM and route every line directly into Gradle's console logger stream.

2. **Immediate Execution of Instrumented Components**:
   Unit tests directly instantiate and invoke classes (`DefaultDailyContextRepository`, `LocalFirstRepository`, `BufferProvider`, `HasherProvider`, `SyncCoordinator`) that have explicit logging calls (`logger.v { ... }`, `logger.d { ... }`, `logger.i { ... }`).
   
3. **[TestLoggerModule.kt](file:///home/nova/StudioProjects/MochaMe-KMP/core/test/test-logger/src/commonMain/kotlin/com/mochame/logger/test/TestLoggerModule.kt#L26-L45)**:
   Tests configure both `TestLogWriter` and `CleanLogWriter(Severity.Verbose)` with `@PlatformTag` `"JVMTest"`.

---

## 4. Root Cause Analysis: Why `:app:entry:jvmApp:run` Shows No Logs

When executing `./gradlew clean :app:entry:jvmApp:run`, no log lines are rendered in the terminal. The root causes span four distinct layers:

### Root Cause 1: Zero Application Bootstrap / Lifecycle Logging (Dormancy at Startup)

* **Entry Point Tracing**:
  Look at [app/entry/jvmApp/src/jvmMain/kotlin/com/mochame/app/entry/jvm/Main.kt](file:///home/nova/StudioProjects/MochaMe-KMP/app/entry/jvmApp/src/jvmMain/kotlin/com/mochame/app/entry/jvm/Main.kt#L11-L28):
  ```kotlin
  fun main() {
      initKoinCompose()

      application {
          val windowState = rememberWindowState(
              width = 1024.dp,
              height = 768.dp
          )

          Window(
              onCloseRequest = ::exitApplication,
              state = windowState,
              title = "MochaMe"
          ) {
              window.minimumSize = Dimension(480, 560)
              MochaComposeAppShell()
          }
      }
  }
  ```
* **Koin Initialization**:
  [initKoinCompose()](file:///home/nova/StudioProjects/MochaMe-KMP/app/ui/src/commonMain/kotlin/com/mochame/app/ui/di/KoinInitCompose.kt#L15-L18) calls `startKoin<MochaComposeApp>()`.
  * In Koin, all `@Single` definitions are **lazy** by default.
  * Neither `main()`, `initKoinCompose()`, nor `MochaComposeApp` emits any log statement.
  * `LoggerModule.getLogger()` is not even invoked until a class requesting `Logger` is injected.
* **UI Tree Construction**:
  * [MochaComposeAppShell](file:///home/nova/StudioProjects/MochaMe-KMP/app/ui/src/commonMain/kotlin/com/mochame/app/ui/MochaAppShell.kt#L24-L71) only injects `timeProvider: MochaTimeUtils`.
  * [DashboardScreen](file:///home/nova/StudioProjects/MochaMe-KMP/app/ui/src/commonMain/kotlin/com/mochame/app/ui/screens/DashboardScreen.kt#L23-L78) only injects `timeProvider: MochaTimeUtils` and renders static action buttons ("Log Today", "Edit Yesterday").
  * Neither `MochaTimeUtils` nor `DashboardScreen` injects `Logger` or performs any logging.
* **Background Engine Inactivity**:
  * `SyncCoordinator.startOutbound()` and `SyncJanitor` are never triggered or started during app boot.
* **Result**: While the desktop application window is open and sitting on the Dashboard, **exactly 0 log calls are executed anywhere in the JVM**.

---

### Root Cause 2: Instance-Scoped DI vs Kermit Static Global Configuration

* In Kermit, logging can be performed either via:
  1. An **injected instance** of `co.touchlab.kermit.Logger`.
  2. The **static companion object** `co.touchlab.kermit.Logger.v { ... }` or `co.touchlab.kermit.Logger.i { ... }`.
* In `MochaMe-KMP`, `CleanLogWriter` is **only** registered inside the Koin-managed `Logger` singleton provided by `LoggerModule`.
* Kermit's global static companion is never configured in `main()`. If any component or third-party code calls static `co.touchlab.kermit.Logger`, Kermit uses its default `CommonWriter` rather than `CleanLogWriter`.

---

### Root Cause 3: Mutation-Only Logging Pattern

* Throughout the codebase, logging statements exist predominantly inside **write pipelines** and **concurrency locks**:
  * [LocalFirstRepository.handleLocalCommit](file:///home/nova/StudioProjects/MochaMe-KMP/core/sync-api/src/commonMain/kotlin/com/mochame/sync/api/repository/LocalFirstRepository.kt#L303-L339) (emits `"In-Memory Summary: ..."` and `"Local DB Transaction Committed [...]"`).
  * [DefaultNodeContextManager.getOrEstablishContext](file:///home/nova/StudioProjects/MochaMe-KMP/node/src/commonMain/kotlin/com/mochame/node/managers/DefaultNodeContextManager.kt#L60) (emits `"Node Fetched. Id: ..."`).
  * Buffer allocation and SHA-256 hashing.
* In the runtime app, these methods are only reached when a user enters the Daily Context form and clicks **"Save Changes"**. Until a mutation is triggered, no repository writes occur, and no logs are produced.

---

### Root Cause 4: Gradle `JavaExec` Stream Binding & Interactive Terminal Handling

* In [app/entry/jvmApp/build.gradle.kts](file:///home/nova/StudioProjects/MochaMe-KMP/app/entry/jvmApp/build.gradle.kts#L28-L32):
  ```kotlin
  tasks.withType<JavaExec>().configureEach {
      standardOutput = System.out
      errorOutput = System.err
      standardInput = System.`in`
  }
  ```
* **Configuration-Time Binding**: `standardOutput = System.out` in `configureEach` binds the `JavaExec` process output to Gradle Daemon's `System.out` at Gradle *configuration phase*.
* **Gradle Console Manager**: When running `./gradlew :app:entry:jvmApp:run` in rich interactive mode (default on Linux terminals), Gradle's console renderer manages terminal redraws and cursor positions for the active task status line.
* When the running JVM process emits nothing at startup, Gradle's task bar sits at `> Task :app:entry:jvmApp:run` with no text to render.

---

## 5. Comparison Matrix: Test Run vs Desktop Runtime

| Dimension | `./gradlew :core:platform:jvmTest` | `./gradlew :app:entry:jvmApp:run` |
| :--- | :--- | :--- |
| **Gradle Task Type** | `AbstractTestTask` / `KotlinJvmTest` | `JavaExec` (registered by Compose Desktop) |
| **Gradle Stream Redirection** | `showStandardStreams = true` (explicitly forwarded by test listener) | `standardOutput = System.out` on `JavaExec` |
| **Koin Logger Lifecycle** | Instantiated immediately by test setup modules | Lazy `@Single`, uninstantiated until needed |
| **Component Activity on Start** | Immediately executes test transactions, hashing, and buffer pooling | Idle Compose UI event loop waiting for window inputs |
| **Log Statements Executed** | 10–50+ log statements per test suite | 0 log statements at startup |
| **ANSI TrueColor Rendering** | Displayed in test output blocks | Silent due to absence of emitted logs |

---

## 6. Blueprint for Pop!_OS COSMIC Runtime Logging

To establish full runtime log observability with [CleanLogWriter](file:///home/nova/StudioProjects/MochaMe-KMP/core/logger/src/commonMain/kotlin/com/mochame/logger/CleanLogWriter.kt#L7-L52) on **Pop!_OS COSMIC**, the following non-invasive steps are recommended:

### Step 1: Application Bootstrap & UI Lifecycle Logging

Add lifecycle logging during the startup sequence in `app:entry:jvmApp` and `app:ui`:

1. **Bootstrap Logging in [Main.kt](file:///home/nova/StudioProjects/MochaMe-KMP/app/entry/jvmApp/src/jvmMain/kotlin/com/mochame/app/entry/jvm/Main.kt)**:
   * Resolve `Logger` from Koin immediately after `initKoinCompose()`.
   * Log application startup, platform details, and window configuration:
     ```kotlin
     val koinApp = initKoinCompose()
     val logger = koinApp.koin.get<Logger>().withTags(LogTags.Layer.BOOT, LogTags.Domain.PLATFORM, "Main")
     logger.i { "MochaMe Desktop initialized on JVM (Pop!_OS COSMIC)" }
     ```

2. **Navigation & Screen Logging in [MochaAppShell.kt](file:///home/nova/StudioProjects/MochaMe-KMP/app/ui/src/commonMain/kotlin/com/mochame/app/ui/MochaAppShell.kt)**:
   * Log destination navigation events and user screen entries:
     ```kotlin
     logger.d { "Navigating to Destination: ${destination::class.simpleName}" }
     ```

3. **ViewModel Intent Logging in [DailyContextViewModel.kt](file:///home/nova/StudioProjects/MochaMe-KMP/feature/bio/ui/src/commonMain/kotlin/com/mochame/bio/ui/DailyContextViewModel.kt)**:
   * Log UI intents (`Save`, `Delete`, `ToggleNapped`) when dispatched by the user.

---

### Step 2: Initialize Kermit Static Logger Globally

Ensure that any un-injected or static Kermit logger invocations also use [CleanLogWriter](file:///home/nova/StudioProjects/MochaMe-KMP/core/logger/src/commonMain/kotlin/com/mochame/logger/CleanLogWriter.kt#L7-L52):

```kotlin
// In initKoinCompose or Main.kt before Koin start
co.touchlab.kermit.Logger.setLogWriters(CleanLogWriter(Severity.Verbose))
co.touchlab.kermit.Logger.setMinSeverity(Severity.Verbose)
co.touchlab.kermit.Logger.setTag("Mocha")
```

---

### Step 3: Gradle Execution Options on Pop!_OS COSMIC

Pop!_OS COSMIC's default terminal (`cosmic-term`), GNOME Terminal, and Alacritty fully support 24-bit TrueColor escape codes. To ensure Gradle does not buffer stdout or interfere with ANSI color sequences when running the application:

1. Run with plain console mode:
   ```bash
   ./gradlew :app:entry:jvmApp:run --console=plain
   ```
2. Or ensure `standardOutput` inheritance is explicitly retained in `app/entry/jvmApp/build.gradle.kts`.

---

## 7. Verification Checklist

- [x] Swept `:core:logger` implementation (`CleanLogWriter`, `LoggerModule`, `LoggerUtils`, `JvmLoggerUtils`).
- [x] Swept `:core:test:test-logger` implementation (`TestLoggerModule`, `TestLogWriter`, `JvmTestLogger`).
- [x] Swept `:build-logic` convention plugins and `showStandardStreams = true` test task configuration.
- [x] Traced `:app:entry:jvmApp:run` execution flow from `Main.kt` through `initKoinCompose()` and `MochaComposeAppShell()`.
- [x] Identified why 0 logs are emitted at app startup vs test execution.
- [x] Documented architectural findings and Pop!_OS COSMIC roadmap in `/docs`.
