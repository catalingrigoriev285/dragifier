# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Dragifier is a visual RAD IDE (DevelNext-style) for building JavaFX desktop apps by drag and drop.
The IDE is a JavaFX app; the apps it produces are plain-JavaFX Java sources it generates, compiles
and launches. Java 26 toolchain, JavaFX 26, Gradle 9.7.1 wrapper, Windows-targeted.

## Commands

```
gradlew run            # launch the IDE
gradlew smoke          # headless verification: codegen → in-process javac compile, plus
                       # undo/redo, rename, source map, styling, templates, nesting, docking,
                       # form-order checks. Exits non-zero on failure.
gradlew packageSmoke   # full compile → jar → jpackage pipeline (slow, ~minutes)
                       #   -PpackageDest=<dir>  keep the output instead of a temp dir
                       #   -PpackageWeb         exercise the WebView/classpath packaging path
gradlew aiSmoke        # headless verification of the AI edit protocol: prompt coverage, op
                       # applier, digest, reply parsing, and a full turn through a canned
                       # transport. No API key and no network needed.
                       #   --args=--print-prompt   dump the generated system prompt
gradlew build          # compile only; there is no JUnit suite
```

There is no `src/test`. All verification lives in `main()` classes wired to Gradle `JavaExec`
tasks: [CodegenSmoke.java](src/main/java/dev/dragifier/CodegenSmoke.java),
[PackageSmoke.java](src/main/java/dev/dragifier/PackageSmoke.java) and
[AiSmoke.java](src/main/java/dev/dragifier/AiSmoke.java). `CodegenSmoke.main` builds one
project exercising every component family, compiles it, then calls a series of `check*` methods —
add a new `private static void checkX(...)` and call it from `main` to cover new behavior. To run a
subset, run the class directly (`gradlew smoke` runs all of it; it is fast).

After a substantive change, run `gradlew smoke` and also launch the IDE (`gradlew run`, in the
background) and check for exceptions in the log — much of the code is UI wiring the smoke test
cannot reach. The user often takes over the launched window and tests interactively.

## Architecture

### One model, four consumers that must agree

`ProjectModel` → `FormModel` (one per form) → flat `List<FormComponent>`. Plain Gson-serialized data
objects; the JSON *is* the `.dragifier` project file, so field names are the file format and fields
must not be `final` (Gson assigns reflectively). Nesting is **not** a tree: `FormComponent.parentId`
plus `slot` (tab/pane index, grid cell `"col,row"`, dock region) expresses hierarchy, and **list
order is z-order and focus order** among siblings. Child `x`/`y` are relative to the parent's
*content area*, not the parent's bounds.

Four layers render that same model and must stay behaviorally identical:

| Layer | File | Role |
|---|---|---|
| Node factory | [Renderer.java](src/main/java/dev/dragifier/ui/Renderer.java) | model → live JavaFX node; `contentPanes()` exposes container slots |
| Design surface | [DesignCanvas.java](src/main/java/dev/dragifier/ui/DesignCanvas.java) | wrappers, selection, drag/resize/re-parent, guides |
| Quick preview | [LiveBuilder.java](src/main/java/dev/dragifier/ui/LiveBuilder.java) | mirrors what generated code produces |
| Real output | [JavaCodeGenerator.java](src/main/java/dev/dragifier/codegen/JavaCodeGenerator.java) | one `Stage` subclass per form + `Main`/`Launcher` |

[ContainerGeometry.java](src/main/java/dev/dragifier/model/ContainerGeometry.java) (content-area
sizes, slot counts, divider positions) and
[DockLayout.java](src/main/java/dev/dragifier/model/DockLayout.java) (Delphi-style edge docking,
resolved to constant AnchorPane anchors) are the shared authority — geometry decisions belong there,
not duplicated per layer. Changing layout behavior in one layer without the others is the most
common way to break this codebase.

### Generated apps have no third-party dependencies

`RuntimeApi.SOURCE` and `FileBrowserApi.SOURCE` are Java text blocks emitted verbatim into every
generated project (`UI.java`, `FileBrowser.java`). They may use plain JavaFX only. `FileBrowserApi`
is a deliberate duplicate of [FileTreeView.java](src/main/java/dev/dragifier/ui/FileTreeView.java)
(the IDE's design-time copy) — keep the two in sync.

### Run / package pipeline

[AppRunner.java](src/main/java/dev/dragifier/runner/AppRunner.java) writes generated sources to a
temp dir, compiles with the in-process `javac` API, and launches the app as a separate process
reusing the IDE's own JavaFX jars. A [SourceMap](src/main/java/dev/dragifier/codegen/SourceMap.java)
records where each user-authored event line landed, so compile errors resolve back to
form/component/event/line for the Problems dialog.

**javafx.web gotcha:** `javafx.web` needs `jdk.jsobject`, which JDK 26 no longer ships. So:
- The IDE's own `javafx { modules = ... }` in [build.gradle.kts](build.gradle.kts) must **not** list
  `javafx.web` (the IDE fails to boot). The web/media jars ride along as plain `implementation`
  dependencies with the `:win` classifier.
- Projects containing a `WEB_VIEW` run and package in *classpath mode* (via the generated non-
  `Application` `Launcher` class); everything else uses the lean module-path/jlink flow. See
  `AppRunner.usesWeb` / `modulePath()` and the `classpathMode` branches in
  [AppPackager.java](src/main/java/dev/dragifier/packager/AppPackager.java).

### IDE shell

[MainWindow.java](src/main/java/dev/dragifier/ui/MainWindow.java) owns the `ProjectModel`, the
`UndoManager` and all wiring; panes are dumb and report upward through `setOnXxx` callbacks
(`onGeometryChanged`, `onStructureChanged`, `onCheckpoint`, …). Undo is snapshot-based: every step
is a full `ProjectIO.toJson(project)` string, and consecutive checkpoints sharing a non-null tag
coalesce (typing bursts, drag streams). Anything that mutates the model must call
`undoManager.checkpoint(tag)` *before* the mutation and `markDirty()` after.

The inspector is a registry, not an if-chain: every editable property is one `PropertySpec` row
declared in [ComponentProperties.java](src/main/java/dev/dragifier/ui/ComponentProperties.java)
(category, editor kind, `applies` predicate, getter/setter), rendered generically by
[PropertyGrid.java](src/main/java/dev/dragifier/ui/PropertyGrid.java).

`ProjectIO.load` still accepts legacy single-form project files (no `forms` key) by wrapping them —
don't break that path.

### AI assistant

[dev.dragifier.ai](src/main/java/dev/dragifier/ai/) is headless except for `AiSession`. The user
describes an app; the model replies with `{"reply": …, "ops": […]}` and the ops are applied to the
`ProjectModel`. Chat lives in an `AiChatPane` tabbed beside the inspector in the right column.

- **The prompt describes the running code, not a copy of it.** `PromptBuilder` walks
  `ComponentType.values()`, `EventSpec.forType`, `OpApplier.patchableKeys()`,
  `CompletionCatalog.methodsFor` and the `public static` signatures inside `RuntimeApi.SOURCE`. The
  worked example it shows is applied and compiled by `AiSmoke`, so it cannot drift into being wrong.
- **`ProjectDigest`, never `ProjectIO.toJson`, goes into a prompt.** Projects inline images and
  media as Base64; sending the raw JSON would blow the context window and bill for it every turn.
  The digest also drops properties still at their default (~5× smaller).
- **`OpApplier` validates rather than trusts.** Event keys especially: `JavaCodeGenerator`
  *looks up* keys per type and silently drops the ones that don't fit, so a bad key would compile
  cleanly and do nothing — the one class of mistake the compile check cannot catch. Properties are
  applied as a Gson merge patch in the field names the model was shown, so there is no setter table
  here to fall out of date.
- **Threading**: model reads and writes on the FX thread only; the compile check runs against a
  `ProjectIO` deep copy because `JavaCodeGenerator` calls `DockLayout.applyTo`, which writes geometry
  back into the components it is handed; streaming deltas are coalesced to one UI update per pulse.
- One `undoManager.checkpoint(null)` per turn — `null`, not a tag, or consecutive turns would
  coalesce into a single undo step. Exactly one automatic repair round, structurally, not by counter.

## Cross-file checklists

**Adding a component type** — `ComponentType` enum (display name, id prefix, default size,
`Category`, `ContainerKind`) → `Renderer.createNode` + styling helpers → `LiveBuilder` if it needs
special sizing → `JavaCodeGenerator` (`javaTypeFor`, `hasText`, the construction/config switch, event wiring) →
`EventSpec.forType` → `ComponentProperties` (`applies` predicates for its properties) →
`CompletionCatalog.methodsFor` → a case in `CodegenSmoke.main`. The AI system prompt needs no edit:
`PromptBuilder` enumerates the enums, and `AiSmoke.checkPromptCoverage` fails the build if a type or
event key never reaches the prompt.

**Adding a property** — field + getter/setter on `FormComponent` (null-safe getter for Strings, so
old project files load) → `Renderer` application → `JavaCodeGenerator` emission → a `PropertySpec`
row in `ComponentProperties` → `FormModel.copyOne` (hand-maintained, silently drops what it misses)
→ assert it in `CodegenSmoke.checkStyling` or similar. The AI reaches the new property
automatically — `OpApplier` patches through Gson, not a setter table — but give it a line in
`PromptBuilder.propertyNotes` if its value format isn't obvious.

## Conventions

- Javadoc on every class and non-obvious method explaining *why*, in the existing terse style.
- Icons come from Ikonli Feather (`Icons`/`FontIcon`); chrome colors come from AtlantaFX theme
  variables via [dragifier.css](src/main/resources/dragifier.css) — no hardcoded theme colors, so
  dark mode keeps working. `code-highlight.css` styles the RichTextFX editor.
- The IDE is not localized (deliberate).
- The user commits their own work; don't commit unless asked.
