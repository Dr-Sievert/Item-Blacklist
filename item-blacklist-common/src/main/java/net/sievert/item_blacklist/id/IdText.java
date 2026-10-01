package net.sievert.item_blacklist.id;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * A text id "namespace:path", as the config file, log and report lines, loot JSON and tests
 * hold it. It never calls the game's id class, whose name and rules change inside the 1.21.x
 * line, so it validates by the 26.x rule on every release: an entry 26.x refuses is refused
 * everywhere, and only the wording of the warning differs from what 1.21.x would have said.
 * Game code turns one into a key with {@code Keys.of(registry, id.namespace(), id.path())}.
 *
 * @param namespace the namespace, [a-z0-9_.-]+ and not ".."
 * @param path the path, [a-z0-9_./-]+
 */
public record IdText(String namespace, String path) {
    /** The namespace an empty or omitted namespace means, as the game reads one. */
    public static final String DEFAULT_NAMESPACE = "minecraft";

    /**
     * Orders id texts path first, then namespace, split at the first ':' (none: namespace ""),
     * with a leading '#' ignored: the order of the old detailed log. String order breaks ties,
     * so the order is total, consistent with equals, and never throws, whatever the text: the
     * report sorts causes that are no ids with it too.
     */
    public static final Comparator<String> PATH_FIRST = Comparator
            .comparing(IdText::pathOf)
            .thenComparing(IdText::namespaceOf)
            .thenComparing(Comparator.naturalOrder());

    /**
     * Refuses an invalid part, so that an IdText in hand is always a valid id.
     *
     * @throws IllegalArgumentException "not an id: namespace:path" for an invalid part
     */
    public IdText {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!validNamespace(namespace) || !validPath(path)) {
            throw new IllegalArgumentException("not an id: " + namespace + ":" + path);
        }
    }

    /**
     * The config form: a ':' is required, since the old config refused an entry without one; an
     * empty namespace means "minecraft". Empty when the text is null or not a valid id.
     */
    public static Optional<IdText> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int colon = text.indexOf(':');
        if (colon < 0) {
            return Optional.empty();
        }
        String namespace = colon == 0 ? DEFAULT_NAMESPACE : text.substring(0, colon);
        return of(namespace, text.substring(colon + 1));
    }

    /**
     * The data form, as loot JSON writes ids: no ':' means namespace "minecraft". Empty when the
     * text is null or not a valid id.
     */
    public static Optional<IdText> parseLenient(String text) {
        if (text == null) {
            return Optional.empty();
        }
        return text.indexOf(':') < 0 ? of(DEFAULT_NAMESPACE, text) : parse(text);
    }

    /** "namespace:path", the form every log line, record and payload name uses. */
    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    private static Optional<IdText> of(String namespace, String path) {
        return validNamespace(namespace) && validPath(path)
                ? Optional.of(new IdText(namespace, path))
                : Optional.empty();
    }

    /** [a-z0-9_.-]+ and not "..", 26.x's rule (Identifier.isValidNamespace). */
    static boolean validNamespace(String text) {
        if (text.isEmpty() || text.equals("..")) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    /** [a-z0-9_./-]+, 26.x's rule (Identifier.isValidPath); an empty path is refused too. */
    static boolean validPath(String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!(c == '_' || c == '-' || c == '.' || c == '/' || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9'))) {
                return false;
            }
        }
        return true;
    }

    private static String stripped(String text) {
        return text.startsWith("#") ? text.substring(1) : text;
    }

    private static String pathOf(String text) {
        String bare = stripped(text);
        int colon = bare.indexOf(':');
        return colon < 0 ? bare : bare.substring(colon + 1);
    }

    private static String namespaceOf(String text) {
        String bare = stripped(text);
        int colon = bare.indexOf(':');
        return colon < 0 ? "" : bare.substring(0, colon);
    }
}
