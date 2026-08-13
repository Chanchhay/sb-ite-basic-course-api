package kh.edu.istad.ite.shared.enums;

/** How many add-ons a customer may pick out of a set. */
public enum AddOnSelectionRule {
    /** As many as they like. */
    ANY,
    /** No more than {@code maxChoices}. */
    UP_TO
}
