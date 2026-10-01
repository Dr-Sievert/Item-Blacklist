package net.sievert.item_blacklist.report;

import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.report.ReportSnapshot.Kind;
import java.util.EnumMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The report of one running server: open from its start to its stop, flushed after every
 * reload. Every method is synchronized, since loot is parsed on worker threads while the
 * server thread records too; a flush copies and resets under the lock, and the copy is logged
 * outside it. Counts are of distinct (subject, cause) pairs, so a filter that runs twice with
 * the same result does not double a line.
 */
public final class RemovalReport implements Recorder {
    private final Map<Kind, SortedMap<String, SortedSet<String>>> entries =
            new EnumMap<>(Kind.class);
    private int flushes;
    private ReportSnapshot last = ReportSnapshot.empty();

    /** An empty report; public, so scenarios and unit tests can record into their own. */
    public RemovalReport() {
    }

    @Override
    public synchronized void blacklistedItem(String item) {
        add(Kind.ITEM, item, "");
    }

    @Override
    public synchronized void blacklistedTag(String registry, String tag) {
        add(Kind.TAG, "#" + tag, registry);
    }

    @Override
    public synchronized void blacklistedPotion(String potion) {
        add(Kind.POTION, potion, "");
    }

    @Override
    public synchronized void blacklistedEnchantment(String enchantment) {
        add(Kind.ENCHANTMENT, enchantment, "");
    }

    @Override
    public synchronized void tagRemoval(String registry, String tag, String entry) {
        add(Kind.TAG_ENTRY, entry, registry + " #" + tag);
    }

    @Override
    public synchronized void recipeRemoval(String recipe, String cause) {
        add(Kind.RECIPE, recipe, cause);
    }

    @Override
    public synchronized void lootRemoval(String table, String cause) {
        add(Kind.LOOT, table, cause);
    }

    @Override
    public synchronized void brewingRemoval(String mix, String cause) {
        add(Kind.BREWING, mix, cause);
    }

    @Override
    public synchronized void loaderRemoval(String table, String entry) {
        add(Kind.LOADER, table, entry);
    }

    @Override
    public synchronized void tradeRefusal(String merchant, String cause) {
        add(Kind.TRADE, merchant, cause);
    }

    /**
     * Copies the records under the lock, resets them, keeps the copy as the last flush and
     * returns it. The reset happens here, before anything is logged, so a failure while the
     * copy is rendered cannot carry records into the next report.
     */
    public synchronized ReportSnapshot flush(boolean reloadFailed) {
        ReportSnapshot snapshot = new ReportSnapshot(++flushes, reloadFailed, entries);
        entries.clear();
        last = snapshot;
        return snapshot;
    }

    /** The snapshot of the last flush; an empty one with number 0 before the first. */
    public synchronized ReportSnapshot lastFlush() {
        return last;
    }

    /**
     * Stores one pair; a null subject or cause is stored as "", the cause the configured-entry
     * calls use.
     */
    private void add(Kind kind, String subject, String cause) {
        entries.computeIfAbsent(kind, k -> new TreeMap<>(IdText.PATH_FIRST))
                .computeIfAbsent(subject == null ? "" : subject,
                        s -> new TreeSet<>(IdText.PATH_FIRST))
                .add(cause == null ? "" : cause);
    }
}
