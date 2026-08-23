# Dragifier

A visual RAD IDE for building JavaFX desktop apps by drag and drop — in the spirit of
[DevelNext](https://github.com/jphp-group/develnext), but pure Java on a modern stack
(Java 26, JavaFX 26).

## Run

```
gradlew run
```

## What works (milestone 1)

- **Palette → canvas drag and drop**: Button, Label, TextField, TextArea, CheckBox, Slider,
  Panel, ComboBox, ListView, RadioButton, ProgressBar, Hyperlink, Image
- **Visual designer**: click to select, drag to move (8 px grid snap), 8 resize handles,
  arrow keys to nudge (Shift = grid step), Delete to remove
- **Properties inspector**: position/size, text, font size, text color, custom background;
  form title and window size when nothing is selected
- **Save / load** projects as JSON (`*.dragifier`)
- **Quick Preview** (Shift+F5): renders the form as a live window, no compile
- **Export Java Code**: generates a standalone JavaFX `Application` source file for the form

## What works (milestone 2)

- **Event handlers**: select (or double-click) a component and write Java for its event
  ("On click", "On enter", "On value change"...) in the code pane at the bottom.
  Handler code can reference any component on the form by its id, plus `stage`:
  `label1.setText("Hello, " + textField1.getText() + "!");`
- **▶ Run** (F5): generates the Java source, compiles it in-process with the `javac` API,
  and launches your app as a separate Java process. Compile errors pop up with line numbers.
- `gradlew smoke` — headless check that generated form code compiles and undo/redo behaves

## What works (milestone 3)

- **Undo / redo** (Ctrl+Z / Ctrl+Y): snapshot-based, covering every edit — drops, moves,
  resizes, property changes, event code, deletes. Drags and typing bursts coalesce into
  single undo steps.
- **Copy / Paste / Duplicate** (Ctrl+C / Ctrl+V / Ctrl+D): clones a component with a fresh
  id, including all its properties and event code.
- **Component tree**: every component listed under the palette; selection syncs both ways
  with the canvas.

## What works (milestone 4)

- **Package App…** (Project menu): compiles your form and runs `jpackage` to produce a
  self-contained Windows app — a folder with `YourApp.exe` and a bundled Java+JavaFX
  runtime, runnable on machines with no Java installed.
- `gradlew packageSmoke` — headless check of the full compile → jar → jpackage pipeline

## What works (milestone 5)

- **Multi-select**: Ctrl+click to add/remove, drag a marquee on empty canvas, Ctrl+A for all.
  Group drag, nudge, delete, copy/paste and duplicate all operate on the whole selection.
- **Smart alignment guides**: while dragging, dashed guides appear when edges or centers
  line up with other components or the form's center, and the drag snaps to them.
- **Arrange menu**: align left/right/top/bottom, center horizontally/vertically, same size —
  anchored on the first-selected component.

## What works (milestone 6)

- **More components**: ComboBox, ListView, RadioButton, ProgressBar, Hyperlink — with
  per-type properties (an "Items" list for ComboBox/ListView, a progress value) and
  events ("On select" for ComboBox/ListView, "On toggle"/"On click" for the rest).

## What works (milestone 7)

- **Image component with an asset pipeline**: choose an image file in the inspector; it is
  stored Base64-inside the project file (projects stay a single portable `.dragifier` file)
  and bundled as a jar resource into Run builds and packaged exes, loaded via
  `getResourceAsStream`. Dashed placeholder until an image is chosen; "On click" event.

## What works (milestone 8)

- **Multiple forms per project**: the toolbar has a form switcher plus "+ Form",
  "− Form" and "Set Main" (★ marks the startup form). Each form has a Name (its
  generated class name, editable in the inspector) and compiles to its own
  `Stage` subclass, so opening another form from an event handler is plain Java:
  `new Form2().show();`. Old single-form project files load transparently.

## What works (milestone 9)

- **Syntax-highlighted code editor**: the event code pane is a RichTextFX `CodeArea`
  with Java keyword/string/comment/number highlighting and line numbers.

## What works (milestone 10)

- **Package Installer (.exe)…**: produces a Windows setup wizard (with Start-menu entry
  and shortcut) via jpackage. Requires the [WiX toolset](https://wixtoolset.org)
  (`dotnet tool install --global wix`); without it, a clear error explains what to install.
  Both packaging actions now offer to open the output folder when done.

## What works (milestone 11)

- **Tooltip and Disabled** properties on every component (rendered live in the designer,
  emitted in generated code).
- **Form "On show" event**: with nothing selected, the event pane edits the form's own
  events — code runs when the window opens.
- **Canvas zoom**: 50–200% zoom combo in the toolbar; all editing works while zoomed.

## What works (milestone 12)

- **Project templates** (File → New from Template…, Ctrl+Shift+N): Hello World, Counter,
  Login Form, and Two Forms — each a complete working app with event code to learn from.
- **Text alignment** property (Left/Center/Right) for buttons, labels, check/radio boxes,
  hyperlinks and text fields.

## What works (milestone 13)

- **Canvas rulers**: pixel rulers along the design canvas (labeled every 100 px), scaling
  with zoom, plus a live cursor-position readout in the status bar.
- **App icon** (Project → Set App Icon…): a `.png` becomes the window icon of every form
  (bundled as a resource); a `.ico` becomes the packaged exe's icon via `jpackage --icon`.

## What works (milestone 14 — UI overhaul)

- **Modern theme**: AtlantaFX Primer Light by default, View → Dark Theme for a full dark
  mode (including the code editor's colors). All chrome uses theme variables.
- **Professional layout**: forms as editor tabs (★ = startup form, right-click → Set as
  Main, close = delete), icon toolbar (Feather icons), palette/tree and inspector restyled,
  inspector grouped into collapsible Layout / Appearance / Behavior sections.
- **Console tab**: compile progress and the running app's stdout/stderr stream live into
  a bottom tab next to Events — `System.out.println` debugging works.
- **Welcome screen**: on startup — new/open project, template gallery, recent projects
  (also in File → Open Recent, persisted between sessions).
- **Z-order** (Arrange → Bring to Front / Send to Back) and a **grid-snap toggle** in the toolbar.

## What works (milestone 15 — DevelNext parity round)

- **`UI` helper API** in every generated app: `UI.alert / confirm / prompt`, clipboard,
  file dialogs, `UI.openLink`, `UI.row(...)` for tables. **Insert ▾** menu in the event
  editor drops ready-made snippets (dialogs, open form, close window, println…).
- **Timer component** (non-visual, VB-style): interval in ms, "On tick" event compiled
  to a JavaFX Timeline; "Disabled" = don't start automatically (`timer1.play()` in code).
- **New components**: **Table** (columns editor, rows via `table1.getItems().add(UI.row("Ana", "20"))`,
  On select), **WebView** (Text property = start URL), **Media player** (audio/video file
  bundled into the app, autoplay unless Disabled). WebView apps automatically run and
  package with JavaFX on the classpath (javafx.web can't be jlinked on JDKs without
  jdk.jsobject); everything else keeps the lean jlinked runtime.
- **Autocomplete** in the code editor: Ctrl+Space (or typing `.`) suggests component ids,
  `UI`/`stage`, and per-type methods; Enter/Tab inserts, Esc closes.
- **Anchors + resizable forms**: a "Resizable window" form option and L/T/R/B anchor
  checkboxes per component — anchor left+right (or top+bottom) to stretch with the
  window, classic RAD behavior. Focus (tab) order follows z-order (Arrange menu).

## What works (milestone 16 — RAD ergonomics round)

- **Resizable panels**: palette/tree, canvas, and inspector sit in draggable splitters.
- **Component rename** (Id field or F2): `button1` → `saveButton` with every reference in
  the form's event code updated automatically; invalid/duplicate ids are rejected.
- **Canvas context menu** (cut/copy/paste/duplicate/delete, z-order, lock, align) and
  a **Locked** flag — locked components select but can't be moved or resized.
- **Order editor** (Arrange → Order…) and drag-to-reorder in the Components tree —
  precise z-order and focus order.
- **Palette filter** and **property search** in the inspector.
- **Compile errors are navigable**: a Problems dialog lists them; Go to Code selects the
  component, opens the right event, and puts the caret on the offending line.

## Roadmap

- Deferred by choice: behaviors, auto-save/backups, multi-select property editing, duplicate form, game features

## Project layout

- `dev.dragifier.model` — form data model (what gets saved)
- `dev.dragifier.ui` — IDE shell: palette, design canvas, inspector, preview
- `dev.dragifier.io` — project save/load (JSON via Gson)
- `dev.dragifier.codegen` — Java source generation from the model
