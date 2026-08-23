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
- **Preview** (F5): runs the form as a live window
- **Export Java Code**: generates a standalone JavaFX `Application` source file for the form

## Roadmap

- Event handlers: pick a component event ("On click"...), write Java in an embedded code editor
- Compile & run user code in-IDE via the `javac` API
- Component tree view, copy/paste, undo/redo, multi-select and alignment guides
- More components (ImageView, ComboBox, ListView, menus) and per-type properties
- Multiple forms per project, `jpackage`-based packaging into native installers

## Project layout

- `dev.dragifier.model` — form data model (what gets saved)
- `dev.dragifier.ui` — IDE shell: palette, design canvas, inspector, preview
- `dev.dragifier.io` — project save/load (JSON via Gson)
- `dev.dragifier.codegen` — Java source generation from the model
