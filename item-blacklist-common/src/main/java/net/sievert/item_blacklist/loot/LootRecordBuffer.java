package net.sievert.item_blacklist.loot;

import net.sievert.item_blacklist.report.Recorder;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds the lootRemoval records of one table's walk until the table is known to be loaded, so
 * a table that its load conditions drop, or that fails to parse, leaves no line in the report.
 * Every other record goes straight to the target; the walk makes none. One thread, one table:
 * each JSON hook creates its own buffer, and only the target (the report) is shared.
 */
public final class LootRecordBuffer implements Recorder {
    private final Recorder target;
    private final List<String[]> loot = new ArrayList<>();

    /** A buffer in front of target, the running server's report. */
    public LootRecordBuffer(Recorder target) {
        this.target = target;
    }

    /** Buffered until commit(). */
    @Override
    public void lootRemoval(String table, String cause) {
        loot.add(new String[] {table, cause});
    }

    /** Hands every buffered record to the target, in order, once; a later call adds nothing. */
    public void commit() {
        for (String[] record : loot) {
            target.lootRemoval(record[0], record[1]);
        }
        loot.clear();
    }

    // Forwarded at once: should Recorder gain a method, this class stops compiling and
    // forwards it the same way.

    @Override
    public void blacklistedItem(String item) {
        target.blacklistedItem(item);
    }

    @Override
    public void blacklistedTag(String registry, String tag) {
        target.blacklistedTag(registry, tag);
    }

    @Override
    public void blacklistedPotion(String potion) {
        target.blacklistedPotion(potion);
    }

    @Override
    public void blacklistedEnchantment(String enchantment) {
        target.blacklistedEnchantment(enchantment);
    }

    @Override
    public void tagRemoval(String registry, String tag, String entry) {
        target.tagRemoval(registry, tag, entry);
    }

    @Override
    public void recipeRemoval(String recipe, String cause) {
        target.recipeRemoval(recipe, cause);
    }

    @Override
    public void brewingRemoval(String mix, String cause) {
        target.brewingRemoval(mix, cause);
    }

    @Override
    public void loaderRemoval(String table, String entry) {
        target.loaderRemoval(table, entry);
    }

    @Override
    public void tradeRefusal(String merchant, String cause) {
        target.tradeRefusal(merchant, cause);
    }
}
