package net.sievert.item_blacklist.report;

/**
 * What the filters tell the report. Strings in, so the report names no game type and a filter
 * names its ids once, through Keys.name, without '#' on tags. Each call is stored as one
 * (subject, cause) pair of a {@link ReportSnapshot.Kind}; the Javadoc of each method gives the
 * mapping the renderer reads. A filter takes its Recorder as a parameter: a server path passes
 * the running server's report, a client path {@link #NONE}, so a client never writes into a
 * server's report, not even in singleplayer.
 */
public interface Recorder {
    /** Records nothing: for client paths and for rules called outside a server. */
    Recorder NONE = new Recorder() {
        @Override
        public void blacklistedItem(String item) {
        }

        @Override
        public void blacklistedTag(String registry, String tag) {
        }

        @Override
        public void blacklistedPotion(String potion) {
        }

        @Override
        public void blacklistedEnchantment(String enchantment) {
        }

        @Override
        public void tagRemoval(String registry, String tag, String entry) {
        }

        @Override
        public void recipeRemoval(String recipe, String cause) {
        }

        @Override
        public void lootRemoval(String table, String cause) {
        }

        @Override
        public void brewingRemoval(String mix, String cause) {
        }

        @Override
        public void loaderRemoval(String table, String entry) {
        }

        @Override
        public void tradeRefusal(String merchant, String cause) {
        }
    };

    /** A configured item: kind ITEM, subject the item's id, cause "". */
    void blacklistedItem(String item);

    /**
     * A configured tag cleared: kind TAG, subject "#" + tag, cause the registry ("item",
     * "block", "enchantment", "potion"), so one id in two registries is two entries.
     */
    void blacklistedTag(String registry, String tag);

    /** A configured potion: kind POTION, subject the potion's id, cause "". */
    void blacklistedPotion(String potion);

    /** A configured enchantment: kind ENCHANTMENT, subject its id, cause "". */
    void blacklistedEnchantment(String enchantment);

    /**
     * An entry stripped from a tag: kind TAG_ENTRY, subject the entry's id, cause
     * registry + " #" + tag, the registry being "item", "block", "enchantment" or "potion".
     */
    void tagRemoval(String registry, String tag, String entry);

    /** A recipe removed: kind RECIPE, subject the recipe's id, cause an id or "#" + tag. */
    void recipeRemoval(String recipe, String cause);

    /** A loot entry removed: kind LOOT, subject the table's id, cause an id or "#" + tag. */
    void lootRemoval(String table, String cause);

    /** A brewing mix removed: kind BREWING, subject the mix, cause an id or "#" + tag. */
    void brewingRemoval(String mix, String cause);

    /**
     * An entry removed from a loader's table: kind LOADER, subject the table ("compost",
     * "neoforge:brewing", a data map's name), cause the entry.
     */
    void loaderRemoval(String table, String entry);

    /**
     * A trade offer refused: kind TRADE, subject the merchant, cause an id, or a reason text
     * such as TradeRules.NO_POTION.
     */
    void tradeRefusal(String merchant, String cause);
}
