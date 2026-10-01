package net.sievert.item_blacklist.config;

import net.sievert.item_blacklist.id.IdText;
import java.util.List;

/**
 * The config as read: each list in file order, without duplicates, and immutable, so that
 * nothing validates entries away from it later (the old mod edited its config while it
 * resolved it). Unknown entries stay in; the resolver reports them per server.
 *
 * @param detailedLog "Detailed Log": the report's detail lines after its summary
 * @param items "Items" entries without '#'
 * @param itemTags "Items" entries with '#', stored without it
 * @param potions "Potions" entries
 * @param enchantments "Enchantments" entries without '#'
 * @param enchantmentTags "Enchantments" entries with '#', stored without it
 */
public record BlacklistConfig(boolean detailedLog,
        List<IdText> items, List<IdText> itemTags, List<IdText> potions,
        List<IdText> enchantments, List<IdText> enchantmentTags) {
    /** No entry and no detail lines: what every failure to read the config falls back to. */
    public static final BlacklistConfig EMPTY =
            new BlacklistConfig(false, List.of(), List.of(), List.of(), List.of(), List.of());

    /** Copies each list (unmodifiable, null-free), so the caller's list can change freely. */
    public BlacklistConfig {
        items = List.copyOf(items);
        itemTags = List.copyOf(itemTags);
        potions = List.copyOf(potions);
        enchantments = List.copyOf(enchantments);
        enchantmentTags = List.copyOf(enchantmentTags);
    }

    /** No entry in any list; "Detailed Log" does not count, it blacklists nothing. */
    public boolean isEmpty() {
        return items.isEmpty() && itemTags.isEmpty() && potions.isEmpty()
                && enchantments.isEmpty() && enchantmentTags.isEmpty();
    }

    /**
     * Five entries, one per list in the record's order, each "list: n minecraft, m other", e.g.
     * "items: 8 minecraft, 1 other": the load line's summary, which shows at a glance whether
     * another mod's entries were read.
     */
    public List<String> summary() {
        return List.of(count("items", items), count("item tags", itemTags),
                count("potions", potions), count("enchantments", enchantments),
                count("enchantment tags", enchantmentTags));
    }

    private static String count(String list, List<IdText> ids) {
        long vanilla = ids.stream()
                .filter(id -> id.namespace().equals(IdText.DEFAULT_NAMESPACE))
                .count();
        return list + ": " + vanilla + " minecraft, " + (ids.size() - vanilla) + " other";
    }
}
