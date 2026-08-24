package dev.dragifier.codegen;

/**
 * The {@code FileBrowser.java} class emitted into projects that use the
 * FileBrowser component: a lazy folder tree with a root path, extension
 * filters and select/open callbacks. Plain JavaFX only.
 *
 * <p>Keep in sync with {@code dev.dragifier.ui.FileTreeView}, the IDE's copy
 * used to render the component on the design canvas and in the preview.
 */
public final class FileBrowserApi {

    public static final String FILE_NAME = "FileBrowser.java";

    private FileBrowserApi() {}

    public static final String SOURCE = """
            import javafx.collections.ObservableList;
            import javafx.scene.control.TreeCell;
            import javafx.scene.control.TreeItem;
            import javafx.scene.control.TreeView;
            import javafx.scene.input.KeyCode;

            import java.io.File;
            import java.util.ArrayList;
            import java.util.Arrays;
            import java.util.List;
            import java.util.function.Consumer;

            /** A folder tree: {@code fileBrowser1.getSelectedPath()}, {@code setRoot("C:/data")}, {@code setFilters("txt")}. */
            public class FileBrowser extends TreeView<File> {

                private String[] filters = new String[0];
                private Consumer<File> onFileSelected;
                private Consumer<File> onFileOpened;

                public FileBrowser() {
                    setCellFactory(v -> new TreeCell<File>() {
                        @Override
                        protected void updateItem(File item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                                return;
                            }
                            boolean root = getTreeItem() != null && getTreeItem().getParent() == null;
                            String name = item.getName();
                            setText(root || name.isEmpty() ? item.getAbsolutePath() : name);
                        }
                    });
                    getSelectionModel().selectedItemProperty().addListener((obs, was, item) -> {
                        if (onFileSelected != null && item != null) {
                            onFileSelected.accept(item.getValue());
                        }
                    });
                    setOnMouseClicked(e -> {
                        if (e.getClickCount() == 2) {
                            fireOpen();
                        }
                    });
                    setOnKeyPressed(e -> {
                        if (e.getCode() == KeyCode.ENTER) {
                            fireOpen();
                        }
                    });
                    setRoot("");
                }

                private void fireOpen() {
                    File f = getSelectedFile();
                    if (f != null && f.isFile() && onFileOpened != null) {
                        onFileOpened.accept(f);
                    }
                }

                /** Shows the given folder (blank = the user's home folder). */
                public void setRoot(String path) {
                    File dir = path == null || path.isBlank() ? new File(System.getProperty("user.home")) : new File(path);
                    TreeItem<File> root = new LazyItem(dir);
                    root.setExpanded(true);
                    super.setRoot(root);
                }

                public String getRootPath() {
                    return getRoot() == null || getRoot().getValue() == null ? "" : getRoot().getValue().getAbsolutePath();
                }

                /** Re-reads the folder from disk. */
                public void reload() {
                    setRoot(getRootPath());
                }

                /** Only files with these extensions are listed (folders always are); none = all files. */
                public void setFilters(String... extensions) {
                    String[] next = extensions == null ? new String[0] : extensions;
                    if (!Arrays.equals(next, filters)) {
                        filters = next;
                        reload();
                    }
                }

                public File getSelectedFile() {
                    TreeItem<File> item = getSelectionModel().getSelectedItem();
                    return item == null ? null : item.getValue();
                }

                public String getSelectedPath() {
                    File f = getSelectedFile();
                    return f == null ? null : f.getAbsolutePath();
                }

                /** Called with the file or folder whenever the selection changes. */
                public void setOnFileSelected(Consumer<File> handler) {
                    onFileSelected = handler;
                }

                /** Called with the file on double-click or Enter (folders just expand). */
                public void setOnFileOpened(Consumer<File> handler) {
                    onFileOpened = handler;
                }

                private boolean accepts(File f) {
                    if (f.isHidden()) {
                        return false;
                    }
                    if (f.isDirectory() || filters.length == 0) {
                        return true;
                    }
                    String name = f.getName().toLowerCase();
                    for (String ext : filters) {
                        String e = ext == null ? "" : ext.trim().toLowerCase();
                        if (e.startsWith("*.")) {
                            e = e.substring(2);
                        } else if (e.startsWith(".")) {
                            e = e.substring(1);
                        }
                        if (!e.isEmpty() && name.endsWith("." + e)) {
                            return true;
                        }
                    }
                    return false;
                }

                /** Loads a folder's entries the first time it is expanded. */
                private class LazyItem extends TreeItem<File> {
                    private boolean loaded;

                    LazyItem(File file) {
                        super(file);
                    }

                    @Override
                    public boolean isLeaf() {
                        return !getValue().isDirectory();
                    }

                    @Override
                    public ObservableList<TreeItem<File>> getChildren() {
                        if (!loaded) {
                            loaded = true;
                            File[] files = getValue().listFiles();
                            List<TreeItem<File>> items = new ArrayList<>();
                            if (files != null) {
                                Arrays.sort(files, (a, b) -> a.isDirectory() != b.isDirectory()
                                        ? (a.isDirectory() ? -1 : 1)
                                        : a.getName().compareToIgnoreCase(b.getName()));
                                for (File f : files) {
                                    if (accepts(f)) {
                                        items.add(new LazyItem(f));
                                    }
                                }
                            }
                            super.getChildren().setAll(items);
                        }
                        return super.getChildren();
                    }
                }
            }
            """;
}
