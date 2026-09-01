package net.sievert.item_blacklist.util;

import net.sievert.item_blacklist.ItemBlacklist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ItemBlacklistLogs {

    private ItemBlacklistLogs() {}

    private static final String LOGGER_NAME = ItemBlacklist.MOD_ID.toUpperCase();
    private static final Logger LOGGER = LoggerFactory.getLogger(LOGGER_NAME);

    /* ---------------------------------------------------------------------
     * Debug
     * ------------------------------------------------------------------ */

    public static void debug(ItemBlacklistLogTags tag, String message, Object... args) {
        if (!LOGGER.isDebugEnabled()) return;
        log(Level.DEBUG, tag, message, args);
    }

    /* ---------------------------------------------------------------------
     * Info
     * ------------------------------------------------------------------ */

    public static void info(ItemBlacklistLogTags tag, String message, Object... args) {
        log(Level.INFO, tag, message, args);
    }

    /* ---------------------------------------------------------------------
     * Warn
     * ------------------------------------------------------------------ */

    public static void warn(ItemBlacklistLogTags tag, String message, Object... args) {
        log(Level.WARN, tag, message, args);
    }

    /* ---------------------------------------------------------------------
     * Error
     * ------------------------------------------------------------------ */

    public static void error(ItemBlacklistLogTags tag, String message, Object... args) {
        log(Level.ERROR, tag, message, args);
    }

    /* ---------------------------------------------------------------------
     * Internal
     * ------------------------------------------------------------------ */

    private enum Level { DEBUG, INFO, WARN, ERROR }

    private static void log(
            Level level,
            ItemBlacklistLogTags tag,
            String message,
            Object... args
    ) {
        String prefixed = prefix(tag.getId(), message);

        switch (level) {
            case DEBUG -> LOGGER.debug(prefixed, args);
            case INFO -> LOGGER.info(prefixed, args);
            case WARN -> LOGGER.warn(prefixed, args);
            case ERROR -> LOGGER.error(prefixed, args);
        }
    }

    private static String prefix(String tag, String message) {
        return "[" + tag + "] " + message;
    }
}