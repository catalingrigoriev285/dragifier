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

## Roadmap
- Installer output (`.msi`/`.exe` setup) for machines with the WiX toolset
- Syntax-highlighted code editor (RichTextFX)

## Project layout

- `dev.dragifier.model` — form data model (what gets saved)
- `dev.dragifier.ui` — IDE shell: palette, design canvas, inspector, preview
- `dev.dragifier.io` — project save/load (JSON via Gson)
- `dev.dragifier.codegen` — Java source generation from the model
