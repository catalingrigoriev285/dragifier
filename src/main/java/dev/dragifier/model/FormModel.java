package dev.dragifier.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The whole form being designed: window properties plus placed components.
 * Components are kept in one flat list; nesting is expressed through
 * {@link FormComponent#getParentId()} and list order is the z-order (and
 * focus order) among siblings.
 */
public class FormModel {
    /** Unique form name; becomes the generated Java class name. */
    private String name = "Form1";
    private String title = "My App";
    private double width = 640;
    private double height = 480;
    private boolean resizable = false;
    // not final: Gson assigns it reflectively on load, which the JDK blocks for final fields
    private List<FormComponent> components = new ArrayList<>();
    private Map<String, String> events = new LinkedHashMap<>();

    public FormComponent create(ComponentType type, double x, double y) {
        return create(type, x, y, null, "");
    }

    /** Creates a component inside {@code parent} (null = the form) at the given slot. */
    public FormComponent create(ComponentType type, double x, double y, FormComponent parent, String slot) {
        FormComponent c = new FormComponent();
        c.setId(nextId(type));
        c.setType(type);
        c.setX(x);
        c.setY(y);
        c.setWidth(type.defaultWidth);
        c.setHeight(type.defaultHeight);
        c.setText(type.defaultText);
        c.setParentId(parent == null ? null : parent.getId());
        c.setSlot(slot);
        switch (type) {
            case TIMER -> c.setValue(1000); // interval in ms
            case TAB_PANE -> c.setItems("Tab 1\nTab 2");
            case SPLIT_PANE -> {
                c.setOrientation("HORIZONTAL");
                c.setPanes(2);
            }
            case STACK_PANEL -> {
                c.setOrientation("VERTICAL");
                c.setSpacing(6);
            }
            case GRID_PANE -> {
                c.setGridColumns(2);
                c.setGridRows(2);
                c.setSpacing(4);
            }
            default -> { }
        }
        components.add(c);
        return c;
    }

    /** Removes a component together with everything nested inside it. */
    public void remove(FormComponent c) {
        components.removeAll(subtree(c));
    }

    // ------------------------------------------------------------ hierarchy

    public FormComponent findById(String id) {
        if (id == null) {
            return null;
        }
        for (FormComponent c : components) {
            if (id.equals(c.getId())) {
                return c;
            }
        }
        return null;
    }

    /** The container holding {@code c}, or null when it sits directly on the form. */
    public FormComponent parentOf(FormComponent c) {
        return findById(c.getParentId());
    }

    /** Direct children of {@code parent} (null = top-level components), in z-order. */
    public List<FormComponent> childrenOf(FormComponent parent) {
        String pid = parent == null ? null : parent.getId();
        List<FormComponent> out = new ArrayList<>();
        for (FormComponent c : components) {
            if (Objects.equals(pid, c.getParentId())) {
                out.add(c);
            }
        }
        return out;
    }

    /** Siblings of {@code c} including itself, in z-order. */
    public List<FormComponent> siblingsOf(FormComponent c) {
        return childrenOf(parentOf(c));
    }

    /** True when {@code ancestor} contains {@code c} at any depth. */
    public boolean isAncestor(FormComponent ancestor, FormComponent c) {
        FormComponent p = parentOf(c);
        int guard = 0;
        while (p != null && guard++ < 10_000) {
            if (p == ancestor) {
                return true;
            }
            p = parentOf(p);
        }
        return false;
    }

    /** {@code c} followed by all its descendants, pre-order. */
    public List<FormComponent> subtree(FormComponent c) {
        List<FormComponent> out = new ArrayList<>();
        collect(c, out);
        return out;
    }

    private void collect(FormComponent c, List<FormComponent> out) {
        out.add(c);
        for (FormComponent child : childrenOf(c)) {
            collect(child, out);
        }
    }

    /** Every component in tree order (parents before their children, siblings in z-order). */
    public List<FormComponent> walk() {
        List<FormComponent> out = new ArrayList<>();
        for (FormComponent top : childrenOf(null)) {
            collect(top, out);
        }
        // orphans (dangling parentId) still show up rather than vanishing
        for (FormComponent c : components) {
            if (!out.contains(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /** Nesting depth: 0 for top-level components. */
    public int depth(FormComponent c) {
        int d = 0;
        FormComponent p = parentOf(c);
        while (p != null && d < 10_000) {
            d++;
            p = parentOf(p);
        }
        return d;
    }

    /**
     * Moves {@code c} (with its subtree) into {@code newParent} (null = form) at
     * {@code slot}. Refused when that would nest a container inside itself.
     */
    public boolean reparent(FormComponent c, FormComponent newParent, String slot) {
        if (newParent == c || (newParent != null && isAncestor(c, newParent))) {
            return false;
        }
        if (newParent != null && !newParent.getType().isContainer()) {
            return false;
        }
        c.setParentId(newParent == null ? null : newParent.getId());
        c.setSlot(slot == null ? "" : slot);
        return true;
    }

    /** Replaces the order of one sibling group; {@code order} must contain exactly those siblings. */
    public void reorderSiblings(List<FormComponent> order) {
        if (order.isEmpty()) {
            return;
        }
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < components.size(); i++) {
            if (order.contains(components.get(i))) {
                indices.add(i);
            }
        }
        if (indices.size() != order.size()) {
            return;
        }
        for (int i = 0; i < indices.size(); i++) {
            components.set(indices.get(i), order.get(i));
        }
    }

    /** Moves {@code c} to the end of its sibling group = topmost in z-order. */
    public void toFront(FormComponent c) {
        List<FormComponent> sibs = siblingsOf(c);
        sibs.remove(c);
        sibs.add(c);
        reorderSiblings(sibs);
    }

    /** Moves {@code c} to the start of its sibling group = bottommost in z-order. */
    public void toBack(FormComponent c) {
        List<FormComponent> sibs = siblingsOf(c);
        sibs.remove(c);
        sibs.add(0, c);
        reorderSiblings(sibs);
    }

    // ------------------------------------------------------------ duplicate

    /** Adds a copy of {@code src} (with its subtree) with fresh ids, offset slightly inside its parent. */
    public FormComponent duplicate(FormComponent src) {
        FormComponent parent = parentOf(src);
        double maxW = parent == null ? width : parent.getWidth();
        double maxH = parent == null ? height : parent.getHeight();
        double nx = Math.min(src.getX() + 16, Math.max(0, maxW - src.getWidth()));
        double ny = Math.min(src.getY() + 16, Math.max(0, maxH - src.getHeight()));
        FormComponent copy = copyOne(src, nx, ny, parent, src.getSlot());
        copyChildren(src, copy);
        return copy;
    }

    private void copyChildren(FormComponent src, FormComponent copy) {
        for (FormComponent child : childrenOf(src)) {
            FormComponent cc = copyOne(child, child.getX(), child.getY(), copy, child.getSlot());
            copyChildren(child, cc);
        }
    }

    private FormComponent copyOne(FormComponent src, double nx, double ny, FormComponent parent, String slot) {
        FormComponent c = create(src.getType(), nx, ny, parent, slot);
        c.setWidth(src.getWidth());
        c.setHeight(src.getHeight());
        c.setText(src.getText());
        c.setFontSize(src.getFontSize());
        c.setTextColor(src.getTextColor());
        c.setBackground(src.getBackground());
        c.setItems(src.getItems());
        c.setValue(src.getValue());
        c.setImageData(src.getImageData());
        c.setTooltip(src.getTooltip());
        c.setDisabled(src.isDisabled());
        c.setAlignment(src.getAlignment());
        c.setColumns(src.getColumns());
        c.setMediaData(src.getMediaData());
        c.setMediaFormat(src.getMediaFormat());
        c.setLocked(src.isLocked());
        c.setAnchorLeft(src.isAnchorLeft());
        c.setAnchorTop(src.isAnchorTop());
        c.setAnchorRight(src.isAnchorRight());
        c.setAnchorBottom(src.isAnchorBottom());
        c.setDock(src.getDock());
        c.setOrientation(src.getOrientation());
        c.setSpacing(src.getSpacing());
        c.setGridColumns(src.getGridColumns());
        c.setGridRows(src.getGridRows());
        c.setPanes(src.getPanes());
        c.setDividers(src.getDividers());
        c.getEvents().putAll(src.getEvents());
        return c;
    }

    // --------------------------------------------------------------- naming

    /** True when {@code newId} is a valid, unused id this component could take. */
    public boolean canRename(FormComponent c, String newId) {
        if (newId == null || newId.isEmpty() || Character.isDigit(newId.charAt(0))) {
            return false;
        }
        for (char ch : newId.toCharArray()) {
            if (!Character.isJavaIdentifierPart(ch)) {
                return false;
            }
        }
        if (newId.equals("stage") || newId.equals("root") || newId.equals("UI")) {
            return false;
        }
        return components.stream().noneMatch(other -> other != c && newId.equals(other.getId()));
    }

    /** Renames a component and updates references in this form's event code and its children. */
    public boolean renameComponent(FormComponent c, String newId) {
        if (!canRename(c, newId)) {
            return false;
        }
        String oldId = c.getId();
        c.setId(newId);
        for (FormComponent comp : components) {
            if (oldId.equals(comp.getParentId())) {
                comp.setParentId(newId);
            }
        }
        java.util.regex.Pattern ref = java.util.regex.Pattern.compile(
                "\\b" + java.util.regex.Pattern.quote(oldId) + "\\b");
        for (FormComponent comp : components) {
            comp.getEvents().replaceAll((key, code) -> ref.matcher(code).replaceAll(newId));
        }
        getEvents().replaceAll((key, code) -> ref.matcher(code).replaceAll(newId));
        return true;
    }

    private String nextId(ComponentType type) {
        int n = 1;
        while (hasId(type.idPrefix + n)) {
            n++;
        }
        return type.idPrefix + n;
    }

    private boolean hasId(String id) {
        return components.stream().anyMatch(c -> id.equals(c.getId()));
    }

    public String getName() { return name == null || name.isBlank() ? "Form1" : name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public boolean isResizable() { return resizable; }
    public void setResizable(boolean resizable) { this.resizable = resizable; }

    public List<FormComponent> getComponents() { return components; }

    /** Form-level event key → Java handler body (e.g. "onShown"). */
    public Map<String, String> getEvents() {
        if (events == null) {
            events = new LinkedHashMap<>();
        }
        return events;
    }
}
