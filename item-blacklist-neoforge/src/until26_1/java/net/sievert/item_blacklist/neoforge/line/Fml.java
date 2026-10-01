package net.sievert.item_blacklist.neoforge.line;

import net.sievert.item_blacklist.line.Backends;

/**
 * FML on the 1.21.x line. The production check is the static
 * {@code FMLLoader.isProduction()} in FML 4 to 9 (NeoForge 21.1 to 21.8) and an instance
 * method, reached through {@code FMLLoader.getCurrent()}, in FML 10 (NeoForge 21.9 to
 * 21.11), so it comes from a backend: {@code FmlUntil1_21_9} or {@code FmlSince1_21_9}.
 */
public final class Fml {
    /** The calls that differ; each backend implements them for its window. */
    public interface Backend {
        boolean isProduction();
    }

    private static final Backend BACKEND = Backends.pick("Fml", Backend.class,
            Backends.until("1.21.9", "net.sievert.item_blacklist.neoforge.line.FmlUntil1_21_9"),
            Backends.since("1.21.9", "net.sievert.item_blacklist.neoforge.line.FmlSince1_21_9"));

    private Fml() {
    }

    /** Whether FML runs a production game, not a dev run from the IDE or Gradle. */
    public static boolean isProduction() {
        return BACKEND.isProduction();
    }
}
