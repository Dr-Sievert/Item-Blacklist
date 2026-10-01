package net.sievert.item_blacklist.line;

import net.minecraft.world.InteractionResult;

/**
 * The "consumed, no swing" interaction result on the 1.21.x line. The source text
 * InteractionResult.CONSUME is the same on every release, but its field type changes at 1.21.2
 * (an enum constant, then an InteractionResult.Success of a sealed interface), so a getstatic
 * compiled at 1.21.1 fails with NoSuchFieldError from 1.21.2. This facade hands the read to
 * {@code InteractionsUntil1_21_2} (src/until1_21_2) or {@code InteractionsSince1_21_2}
 * (src/since1_21_2), the same source compiled once per window. CLAUDE.md, "A name renamed or
 * removed inside a line".
 */
public final class Interactions {
    /** The read that differs; each backend implements it for its window. */
    public interface Backend {
        /** InteractionResult.CONSUME as the window's jar declares it. */
        InteractionResult consume();
    }

    private static final Backend BACKEND = Backends.pick("Interactions", Backend.class,
            Backends.until("1.21.2", "net.sievert.item_blacklist.line.InteractionsUntil1_21_2"),
            Backends.since("1.21.2", "net.sievert.item_blacklist.line.InteractionsSince1_21_2"));

    private Interactions() {
    }

    /**
     * InteractionResult.CONSUME: the action counts as done and nothing swings. Compare it by
     * identity only; no method of InteractionResult keeps its shape over 1.21.2.
     */
    public static InteractionResult consume() {
        return BACKEND.consume();
    }
}
