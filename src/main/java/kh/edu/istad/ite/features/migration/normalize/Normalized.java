package kh.edu.istad.ite.features.migration.normalize;

/**
 * One value, read.
 *
 * Carries what it became and — when something had to be decided — what rule
 * decided it, so the cleanup step can show the operator every transformation
 * grouped rather than asking them to trust that prices were tidied correctly.
 *
 * @param value the value FluxiBiz will use, or null if it could not be read
 * @param rule  what was applied, or null when the value arrived usable
 * @param problem why it could not be read, or null
 */
public record Normalized(String value, String rule, String problem) {

    public static Normalized asIs(String value) {
        return new Normalized(value, null, null);
    }

    public static Normalized changed(String value, String rule) {
        return new Normalized(value, rule, null);
    }

    public static Normalized unreadable(String problem) {
        return new Normalized(null, null, problem);
    }

    public boolean wasChanged() {
        return rule != null;
    }

    public boolean isUnreadable() {
        return problem != null;
    }
}
