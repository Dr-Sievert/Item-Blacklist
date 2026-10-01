package net.sievert.item_blacklist.line;

/**
 * The line this jar belongs to, 1.21.x, built by the l1_21 modules; for the
 * logs. With both lines each line folder (src/until26_1, src/since26_1) holds its own Line:
 * a class pair, the same name and members in both, which shared code calls directly.
 * CLAUDE.md, "A difference between the lines".
 */
public final class Line {
    /** The line's name as the init line prints it. */
    public static final String NAME = "1.21.x";

    private Line() {
    }
}
