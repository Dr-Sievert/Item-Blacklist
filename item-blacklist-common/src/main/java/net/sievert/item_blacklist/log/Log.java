package net.sievert.item_blacklist.log;

import net.sievert.item_blacklist.ItemBlacklistMod;
import java.util.function.Supplier;

/**
 * Every line of the mod: logger item_blacklist, message "Item Blacklist [TAG] ...". One place
 * writes the prefix, so scripts/run.py and a person reading a log find every line of the mod
 * by its start, and no line carries colour codes or another logger's name. Placeholders are
 * slf4j's "{}".
 */
public final class Log {
    private Log() {
    }

    /**
     * "Item Blacklist [TAG] " + message. Pure: NAME is a compile-time constant, so this loads no
     * logger, and the unit tests call it without a game.
     */
    public static String format(LogTag tag, String message) {
        return ItemBlacklistMod.NAME + " [" + tag.name() + "] " + message;
    }

    /** A line for what went as expected: loads, counts, the report. */
    public static void info(LogTag tag, String message, Object... args) {
        ItemBlacklistMod.LOG.info(format(tag, message), args);
    }

    /** A line for what a config, a datapack or another mod caused and the mod worked around. */
    public static void warn(LogTag tag, String message, Object... args) {
        ItemBlacklistMod.LOG.warn(format(tag, message), args);
    }

    /** A line for a fault of the port or of the environment, which a run must fail on. */
    public static void error(LogTag tag, String message, Object... args) {
        ItemBlacklistMod.LOG.error(format(tag, message), args);
    }

    /** An error line with the cause's stack trace. */
    public static void error(LogTag tag, String message, Throwable cause) {
        ItemBlacklistMod.LOG.error(format(tag, message), cause);
    }

    /**
     * A debug line, built only when debug is on: per-use hooks log here, and no check depends
     * on it, since Fabric's production servers have debug off and NeoForge's have it on.
     */
    public static void debug(LogTag tag, Supplier<String> message) {
        if (ItemBlacklistMod.LOG.isDebugEnabled()) {
            ItemBlacklistMod.LOG.debug(format(tag, message.get()));
        }
    }
}
