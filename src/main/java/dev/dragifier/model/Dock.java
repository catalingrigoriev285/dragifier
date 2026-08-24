package dev.dragifier.model;

/**
 * Delphi/WinForms-style docking: a docked component sticks to that edge of its
 * parent's content area and stretches along it; {@code FILL} takes whatever is left.
 */
public enum Dock {
    NONE, LEFT, TOP, RIGHT, BOTTOM, FILL
}
