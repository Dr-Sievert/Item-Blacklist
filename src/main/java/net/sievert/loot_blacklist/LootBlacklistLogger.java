package net.sievert.loot_blacklist;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;

public final class LootBlacklistLogger {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(LootBlacklist.MOD_ID);

    // Allowed log groups
    public enum Group {
        INIT, VALIDATION, LOOT, RECIPE, TRADE, OTHER
    }

    private static final List<LogEntry> LOG_BUFFER = new ArrayList<>();

    private LootBlacklistLogger() {}

    // --- Log Entry ---
    private static class LogEntry {
        final Group group;
        final String type; // "INFO", "WARN", etc.
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

    // --- BiConsumer accessors for APIs that require consumers ---
    public static BiConsumer<Group, String> infoConsumer()  { return LootBlacklistLogger::info; }
    public static BiConsumer<Group, String> warnConsumer()  { return LootBlacklistLogger::warn; }
    public static BiConsumer<Group, String> errorConsumer() { return LootBlacklistLogger::error; }
    public static BiConsumer<Group, String> debugConsumer() { return LootBlacklistLogger::debug; }

    /** Print all logs in strict group order, then clear buffer. */
    public static void flush() {
        Group[] order = { Group.INIT, Group.VALIDATION, Group.RECIPE, Group.LOOT, Group.TRADE, Group.OTHER };
        EnumSet<Group> seen = EnumSet.noneOf(Group.class);

        for (Group group : order) {
            LOG_BUFFER.stream()
                    .filter(e -> e.group == group)
                    .forEach(LootBlacklistLogger::printLog);
            seen.add(group);
        }
        // Print logs from groups not in the order (if ever added)
        LOG_BUFFER.stream()
                .filter(e -> !seen.contains(e.group))
                .forEach(LootBlacklistLogger::printLog);

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
     * Returns the correct singular or plural word depending on the count.
     * Example: pluralize(1, "entry", "entries") → "entry"
     *          pluralize(5, "entry", "entries") → "entries"
     */
    public static String pluralize(int count, String singular, String plural) {
        return count == 1 ? singular : plural;
    }
}
