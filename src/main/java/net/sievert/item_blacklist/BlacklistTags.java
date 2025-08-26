package net.sievert.item_blacklist;

import net.minecraft.util.Identifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.sievert.item_blacklist.BlacklistLogger.*;
import static net.sievert.item_blacklist.BlacklistLogger.Group.TAG;

public class BlacklistTags {
    private static int totalRemoved = 0;
    private static int expectedRegistryCount = 0;
    private static int finishedCount = 0;
    private static final Map<Identifier, Integer> removedPerTag = new ConcurrentHashMap<>();
    private static final Map<Identifier, List<Identifier>> removedEntriesByTag = new ConcurrentHashMap<>();
    private static boolean warnedNoConfig = false;
    private static boolean summaryPrinted = false;

    private BlacklistTags() {}

    /** Called once before any tag filtering starts (from TagManagerLoaderMixin) */
    public static synchronized void setExpectedTagRegistryCount(int count) {
        expectedRegistryCount = count;
        finishedCount = 0;
        summaryPrinted = false;
        resetStats(); // Always reset stats before a reload
    }

    /** Called by TagGroupLoaderMixin for each tag entry removed */
    public static void reportRemoval(Identifier tagId, Identifier entryId) {
        totalRemoved++;
        removedPerTag.merge(tagId, 1, Integer::sum);
        removedEntriesByTag.computeIfAbsent(tagId, k -> new ArrayList<>()).add(entryId);
    }

    /** Called by TagGroupLoaderMixin if config is missing/empty (only once per reload) */
    public static void warnNoConfig() {
        if (!warnedNoConfig) {
            info(TAG, "No blacklist config present. Skipping tag filter.");
            warnedNoConfig = true;
        }
    }

    /** Called by TagGroupLoaderMixin after each tag registry is filtered.
     *  Fires summary when all registries are complete. */
    public static synchronized void reportRegistryFiltered(boolean detailed) {
        if (expectedRegistryCount <= 0 || summaryPrinted) return;
        finishedCount++;
        if (finishedCount >= expectedRegistryCount) {
            printSummaryAndReset();
            summaryPrinted = true;
            // Set expectedRegistryCount to 0 to avoid further summary prints this reload
            expectedRegistryCount = 0;
        }
    }

    /** Print summary and reset for next reload; should only be called by reportRegistryFiltered */
    private static void printSummaryAndReset() {
        if (totalRemoved == 0) {
            info(TAG, "No blacklisted tag entries found in any registry.");
        } else {
            info(TAG, "Tag blacklist: removed " + totalRemoved + " " +
                    pluralize(totalRemoved, "entry", "entries") + " in " +
                    removedPerTag.size() + " tag" + (removedPerTag.size() == 1 ? "" : "s") + ".");
        }
        resetStats();
    }


    /** Resets all stats for next reload. */
    private static void resetStats() {
        totalRemoved = 0;
        removedPerTag.clear();
        removedEntriesByTag.clear();
        warnedNoConfig = false;
    }
}
