package net.sievert.item_blacklist;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Buffered logger for Item Blacklist.
 * Collects logs by group, then flushes them in a fixed order.
 */
public final class BlacklistLogger {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ItemBlacklist.MOD_ID);

    /** Log groups for ordered output. */
    public enum Group {
        INIT, VALIDATION, LOOT, RECIPE, TRADE, TAG
    }

    private static final List<LogEntry> LOG_BUFFER = new ArrayList<>();

    private BlacklistLogger() {}

    /** Single buffered log entry. */
    private static class LogEntry {
        final Group group;
        final String type;
        final String message;

        LogEntry(Group group, String type, String message) {
            this.group = group;
            this.type = type;
            this.message = message;
        }
    }

    // --- Main log methods ---
    public static void info(Group group, String msg)  { LOG_BUFFER.add(new LogEntry(group, "INFO", msg)); }
    public static void warn(Group group, String msg)  { LOG_BUFFER.add(new LogEntry(group, "WARN", msg)); }
    public static void error(Group group, String msg) { LOG_BUFFER.add(new LogEntry(group, "ERROR", msg)); }
    public static void debug(Group group, String msg) { LOG_BUFFER.add(new LogEntry(group, "DEBUG", msg)); }

    /**
     * Flushes all buffered logs, printing them in strict group order,
     * then clears the buffer.
     */
    public static void flush() {
        Group[] order = { Group.INIT, Group.VALIDATION, Group.LOOT, Group.RECIPE, Group.TRADE, Group.TAG };
        EnumSet<Group> seen = EnumSet.noneOf(Group.class);

        for (Group group : order) {
            LOG_BUFFER.stream()
                    .filter(e -> e.group == group)
                    .forEach(BlacklistLogger::printLog);
            seen.add(group);
        }
        LOG_BUFFER.stream()
                .filter(e -> !seen.contains(e.group))
                .forEach(BlacklistLogger::printLog);

        LOG_BUFFER.clear();
    }

    private static void printLog(LogEntry entry) {
        switch (entry.type) {
            case "ERROR" -> LOGGER.error(entry.message);
            case "WARN"  -> LOGGER.warn(entry.message);
            case "DEBUG" -> LOGGER.debug(entry.message);
            default      -> LOGGER.info(entry.message);
        }
    }

    /**
     * Returns the correct singular or plural word depending on count.
     * Example: pluralize(1, "entry", "entries") → "entry"
     *          pluralize(5, "entry", "entries") → "entries"
     */
    public static String pluralize(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }
}
