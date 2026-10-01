package net.sievert.item_blacklist.line;

/**
 * Release arithmetic, shared by Backends, Capability and the mixin gate. Imports only
 * java.lang, so the mixin plugin may load it before the game (it runs while mixin configs
 * load).
 */
public final class Releases {
    private static final String NUMBER = "\\d+(?:_\\d+)+";
    private static final String FOLDER =
            "since" + NUMBER + "(?:-until" + NUMBER + ")?|until" + NUMBER;

    private Releases() {
    }

    /**
     * Part by part: 1.21.10 is above 1.21.9, 26.1.2 below 26.2, 26.2 equals 26.2.0. A
     * pre-release, release candidate or snapshot of a release (1.21.5-pre1, 1.21.5-rc1,
     * 26.2-rc-1, 26.2-snapshot-1) counts as that release; an id that names none, such as the
     * weekly snapshot 25w14a, is an IllegalArgumentException.
     */
    public static int compare(String a, String b) {
        String[] x = parts(a);
        String[] y = parts(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = Integer.compare(
                    i < x.length ? Integer.parseInt(x[i]) : 0,
                    i < y.length ? Integer.parseInt(y[i]) : 0);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    /** The numbers of a Minecraft id's release part, the text before its first hyphen. */
    private static String[] parts(String id) {
        String release = id.split("-", 2)[0];
        if (!release.matches("\\d+(?:\\.\\d+)*")) {
            throw new IllegalArgumentException("not a Minecraft release: " + id);
        }
        return release.split("\\.");
    }

    /** Whether release is at or above since and below until; null for open. */
    public static boolean inWindow(String release, String since, String until) {
        return (since == null || compare(release, since) >= 0)
                && (until == null || compare(release, until) < 0);
    }

    /**
     * Whether a window folder (since26_2, until1_21_6, since1_21_5-until1_21_6) holds
     * release; IllegalArgumentException("not a window folder: " + folder) for anything else,
     * the empty string included.
     */
    public static boolean inFolder(String folder, String release) {
        if (!folder.matches(FOLDER)) {
            throw new IllegalArgumentException("not a window folder: " + folder);
        }
        String since = null;
        String until = null;
        for (String bound : folder.split("-")) {
            if (bound.startsWith("since")) {
                since = bound.substring("since".length()).replace('_', '.');
            } else {
                until = bound.substring("until".length()).replace('_', '.');
            }
        }
        return inWindow(release, since, until);
    }
}
