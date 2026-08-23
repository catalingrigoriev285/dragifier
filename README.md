# Dragifier

A visual RAD IDE for building JavaFX desktop apps by drag and drop — in the spirit of
[DevelNext](https://github.com/jphp-group/develnext), but pure Java on a modern stack
(Java 26, JavaFX 26).

## Run

```
gradlew run
```

## What works (milestone 1)

- **Palette → canvas drag and drop**: Button, Label, TextField, TextArea, CheckBox, Slider, Panel
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

## Roadmap

- Multi-select and alignment guides
- More components (ImageView, ComboBox, ListView, menus) and per-type properties
- Multiple forms per project, `jpackage`-based packaging into native installers

## Project layout

- `dev.dragifier.model` — form data model (what gets saved)
- `dev.dragifier.ui` — IDE shell: palette, design canvas, inspector, preview
- `dev.dragifier.io` — project save/load (JSON via Gson)
- `dev.dragifier.codegen` — Java source generation from the model
