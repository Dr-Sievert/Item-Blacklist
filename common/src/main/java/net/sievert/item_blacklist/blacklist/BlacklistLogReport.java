package net.sievert.item_blacklist.blacklist;

import net.minecraft.resources.ResourceLocation;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.util.ItemBlacklistLogTags;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.ENCHANTMENT;
import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.ITEM;
import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.LOOT;
import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.POTION;
import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.RECIPE;
import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.TAG;
import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.TRADE;

public final class BlacklistLogReport {

    private static final Map<ResourceLocation, Report> TAGS =
            new LinkedHashMap<>();

    private static final Map<ResourceLocation, Report> ITEMS =
            new LinkedHashMap<>();

    private static final Map<ResourceLocation, Report> POTIONS =
            new LinkedHashMap<>();

    private static final Map<ResourceLocation, Report> ENCHANTMENTS =
            new LinkedHashMap<>();

    private static final Set<ResourceLocation> CLEARED_TAGS =
            new LinkedHashSet<>();

    private static final Set<ResourceLocation> REMOVED_RECIPES =
            new LinkedHashSet<>();

    private static final Set<String> REMOVED_BREWING_RECIPES =
            new LinkedHashSet<>();

    private static final Set<String> REMOVED_TRADES =
            new LinkedHashSet<>();

    private static final Set<ResourceLocation> AFFECTED_LOOT_TABLES =
            new LinkedHashSet<>();

    private static int removedTagEntries;
    private static int removedEnchantmentTagEntries;
    private static int removedLootEntries;

    private BlacklistLogReport() {}

    public static synchronized void reset() {
        TAGS.clear();
        ITEMS.clear();
        POTIONS.clear();
        ENCHANTMENTS.clear();

        CLEARED_TAGS.clear();
        REMOVED_RECIPES.clear();
        REMOVED_BREWING_RECIPES.clear();
        REMOVED_TRADES.clear();
        AFFECTED_LOOT_TABLES.clear();

        removedTagEntries = 0;
        removedEnchantmentTagEntries = 0;
        removedLootEntries = 0;
    }

    public static synchronized void recordBlacklistedItem(
            ResourceLocation item
    ) {
        report(
                ITEMS,
                item
        );
    }

    public static synchronized void recordBlacklistedTag(
            ResourceLocation tag
    ) {
        report(
                TAGS,
                tag
        );

        CLEARED_TAGS.add(tag);
    }

    public static synchronized void recordBlacklistedPotion(
            ResourceLocation potion
    ) {
        report(
                POTIONS,
                potion
        );
    }

    public static synchronized void recordBlacklistedEnchantment(
            ResourceLocation enchantment
    ) {
        report(
                ENCHANTMENTS,
                enchantment
        );
    }

    public static synchronized void recordTagRemoval(
            ResourceLocation item,
            ResourceLocation tag
    ) {
        report(
                ITEMS,
                item
        ).tags.add(tag);

        removedTagEntries++;
    }

    public static synchronized void recordEnchantmentTagRemoval(
            ResourceLocation enchantment,
            ResourceLocation tag
    ) {
        report(
                ENCHANTMENTS,
                enchantment
        ).tags.add(tag);

        removedEnchantmentTagEntries++;
    }

    public static synchronized void recordRecipeTagRemoval(
            ResourceLocation tag,
            ResourceLocation recipe
    ) {
        report(
                TAGS,
                tag
        ).recipes.add(recipe);

        REMOVED_RECIPES.add(recipe);
    }

    public static synchronized void recordRecipeRemoval(
            ResourceLocation item,
            ResourceLocation recipe
    ) {
        report(
                ITEMS,
                item
        ).recipes.add(recipe);

        REMOVED_RECIPES.add(recipe);
    }

    public static synchronized void recordBrewingRecipeItemRemoval(
            ResourceLocation item,
            String recipe,
            String role
    ) {
        report(
                ITEMS,
                item
        ).brewingRecipes.add(
                recipe + " [" + role + ": " + item + "]"
        );

        REMOVED_BREWING_RECIPES.add(recipe);
    }

    public static synchronized void recordBrewingRecipeTagRemoval(
            ResourceLocation tag,
            String recipe,
            String role
    ) {
        report(
                TAGS,
                tag
        ).brewingRecipes.add(
                recipe + " [" + role + ": #" + tag + "]"
        );

        REMOVED_BREWING_RECIPES.add(recipe);
    }

    public static synchronized void recordBrewingRecipePotionRemoval(
            ResourceLocation potion,
            String recipe,
            String role
    ) {
        report(
                POTIONS,
                potion
        ).brewingRecipes.add(
                recipe + " [" + role + ": " + potion + "]"
        );

        REMOVED_BREWING_RECIPES.add(recipe);
    }

    public static synchronized void recordLootTagRemoval(
            ResourceLocation tag,
            ResourceLocation lootTable
    ) {
        report(
                TAGS,
                tag
        ).lootTables.add(lootTable);

        AFFECTED_LOOT_TABLES.add(lootTable);
        removedLootEntries++;
    }

    public static synchronized void recordLootRemoval(
            ResourceLocation item,
            ResourceLocation lootTable
    ) {
        report(
                ITEMS,
                item
        ).lootTables.add(lootTable);

        AFFECTED_LOOT_TABLES.add(lootTable);
        removedLootEntries++;
    }

    public static synchronized void recordTradeTagRemoval(
            ResourceLocation tag,
            String trade
    ) {
        report(
                TAGS,
                tag
        ).trades.add(trade);

        REMOVED_TRADES.add(trade);
    }

    public static synchronized void recordTradeRemoval(
            ResourceLocation item,
            String trade
    ) {
        report(
                ITEMS,
                item
        ).trades.add(trade);

        REMOVED_TRADES.add(trade);
    }

    public static synchronized void flush() {
        if (ItemBlacklist.CONFIG != null
                && ItemBlacklist.CONFIG.detailedLog) {
            logDetails();
        }

        logSummaries();
        reset();
    }

    private static void logDetails() {
        TAGS.keySet()
                .stream()
                .sorted()
                .forEach(tag -> {
                    ItemBlacklistLogs.info(
                            TAG,
                            "Blacklist details for #{}",
                            tag
                    );

                    logAffectedContent(
                            TAGS.get(tag)
                    );
                });

        ITEMS.keySet()
                .stream()
                .sorted()
                .forEach(item -> {
                    Report report =
                            ITEMS.get(item);

                    ItemBlacklistLogs.info(
                            ITEM,
                            "Blacklist details for {}",
                            item
                    );

                    logEntries(
                            report.tags,
                            TAG,
                            "  Removed from tag: {}"
                    );

                    logAffectedContent(report);
                });

        POTIONS.keySet()
                .stream()
                .sorted()
                .forEach(potion -> {
                    ItemBlacklistLogs.info(
                            POTION,
                            "Blacklist details for {}",
                            potion
                    );

                    logAffectedContent(
                            POTIONS.get(potion)
                    );
                });

        ENCHANTMENTS.keySet()
                .stream()
                .sorted()
                .forEach(enchantment -> {
                    Report report =
                            ENCHANTMENTS.get(enchantment);

                    ItemBlacklistLogs.info(
                            ENCHANTMENT,
                            "Blacklist details for {}",
                            enchantment
                    );

                    logEntries(
                            report.tags,
                            TAG,
                            "  Removed from enchantment tag: {}"
                    );

                    logAffectedContent(report);
                });
    }

    private static void logAffectedContent(
            Report report
    ) {
        logEntries(
                report.recipes,
                RECIPE,
                "  Removed recipe: {}"
        );

        logEntries(
                report.brewingRecipes,
                RECIPE,
                "  Removed brewing recipe: {}"
        );

        logEntries(
                report.lootTables,
                LOOT,
                "  Removed from loot table: {}"
        );

        logEntries(
                report.trades,
                TRADE,
                "  Removed trade: {}"
        );
    }

    private static <T extends Comparable<? super T>> void logEntries(
            Set<T> entries,
            ItemBlacklistLogTags tag,
            String message
    ) {
        entries.stream()
                .sorted()
                .forEach(entry ->
                        ItemBlacklistLogs.info(
                                tag,
                                message,
                                entry
                        )
                );
    }

    private static void logSummaries() {
        if (!CLEARED_TAGS.isEmpty()) {
            ItemBlacklistLogs.info(
                    TAG,
                    "Cleared {} blacklisted {}",
                    CLEARED_TAGS.size(),
                    CLEARED_TAGS.size() == 1
                            ? "tag"
                            : "tags"
            );
        }

        if (removedTagEntries > 0) {
            ItemBlacklistLogs.info(
                    TAG,
                    "Removed {} blacklisted item {} from remaining tags",
                    removedTagEntries,
                    removedTagEntries == 1
                            ? "entry"
                            : "entries"
            );
        }

        if (removedEnchantmentTagEntries > 0) {
            ItemBlacklistLogs.info(
                    ENCHANTMENT,
                    "Removed {} blacklisted enchantment {} from enchantment tags",
                    removedEnchantmentTagEntries,
                    removedEnchantmentTagEntries == 1
                            ? "entry"
                            : "entries"
            );
        }

        int removedRecipeCount =
                REMOVED_RECIPES.size()
                        + REMOVED_BREWING_RECIPES.size();

        if (removedRecipeCount > 0) {
            ItemBlacklistLogs.info(
                    RECIPE,
                    "Removed {} disabled {}",
                    removedRecipeCount,
                    removedRecipeCount == 1
                            ? "recipe"
                            : "recipes"
            );
        }

        if (removedLootEntries > 0) {
            ItemBlacklistLogs.info(
                    LOOT,
                    "Removed {} blacklisted loot {} from {} loot {}",
                    removedLootEntries,
                    removedLootEntries == 1
                            ? "entry"
                            : "entries",
                    AFFECTED_LOOT_TABLES.size(),
                    AFFECTED_LOOT_TABLES.size() == 1
                            ? "table"
                            : "tables"
            );
        }

        if (!REMOVED_TRADES.isEmpty()) {
            ItemBlacklistLogs.info(
                    TRADE,
                    "Removed {} blacklisted {}",
                    REMOVED_TRADES.size(),
                    REMOVED_TRADES.size() == 1
                            ? "trade"
                            : "trades"
            );
        }
    }

    private static Report report(
            Map<ResourceLocation, Report> reports,
            ResourceLocation id
    ) {
        return reports.computeIfAbsent(
                id,
                ignored -> new Report()
        );
    }

    private static final class Report {

        private final Set<ResourceLocation> tags =
                new LinkedHashSet<>();

        private final Set<ResourceLocation> recipes =
                new LinkedHashSet<>();

        private final Set<String> brewingRecipes =
                new LinkedHashSet<>();

        private final Set<ResourceLocation> lootTables =
                new LinkedHashSet<>();

        private final Set<String> trades =
                new LinkedHashSet<>();
    }
}