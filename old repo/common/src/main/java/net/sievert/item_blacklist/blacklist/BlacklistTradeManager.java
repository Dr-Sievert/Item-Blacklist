package net.sievert.item_blacklist.blacklist;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.trading.ItemCost;
import net.sievert.item_blacklist.mixin.VillagerTradesAccessor;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.TRADE;

public final class BlacklistTradeManager {

    private static Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> originalTrades;
    private static Int2ObjectMap<VillagerTrades.ItemListing[]> originalWanderingTraderTrades;
    private static Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> originalExperimentalTrades;
    private static List<Pair<VillagerTrades.ItemListing[], Integer>> originalExperimentalWanderingTraderTrades;

    private BlacklistTradeManager() {}

    public static void filter() {
        captureOriginalTrades();

        VillagerTradesAccessor.item_blacklist$setTrades(
                filterProfessionTrades(originalTrades)
        );

        VillagerTradesAccessor.item_blacklist$setWanderingTraderTrades(
                filterTrades(
                        originalWanderingTraderTrades,
                        "Wandering Trader",
                        false
                )
        );

        VillagerTradesAccessor.item_blacklist$setExperimentalTrades(
                filterProfessionTrades(originalExperimentalTrades)
        );

        VillagerTradesAccessor.item_blacklist$setExperimentalWanderingTraderTrades(
                filterExperimentalWanderingTrades(
                        originalExperimentalWanderingTraderTrades
                )
        );
    }

    private static void captureOriginalTrades() {
        if (originalTrades != null) {
            return;
        }

        originalTrades = copyProfessionTrades(
                VillagerTrades.TRADES
        );

        originalWanderingTraderTrades = copyTrades(
                VillagerTrades.WANDERING_TRADER_TRADES
        );

        originalExperimentalTrades = copyProfessionTrades(
                VillagerTrades.EXPERIMENTAL_TRADES
        );

        originalExperimentalWanderingTraderTrades =
                copyExperimentalWanderingTrades();
    }

    private static Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> filterProfessionTrades(
            Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> source
    ) {
        Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> filtered =
                new LinkedHashMap<>();

        for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> entry :
                source.entrySet()) {
            ResourceLocation professionId =
                    BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                            entry.getKey()
                    );

            filtered.put(
                    entry.getKey(),
                    filterTrades(
                            entry.getValue(),
                            formatName(professionId.getPath()),
                            true
                    )
            );
        }

        return filtered;
    }

    private static Int2ObjectMap<VillagerTrades.ItemListing[]> filterTrades(
            Int2ObjectMap<VillagerTrades.ItemListing[]> source,
            String traderName,
            boolean includeLevel
    ) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> filtered =
                new Int2ObjectOpenHashMap<>();

        for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> entry :
                source.int2ObjectEntrySet()) {
            String context = includeLevel
                    ? levelName(entry.getIntKey()) + " " + traderName
                    : traderName;

            filtered.put(
                    entry.getIntKey(),
                    filterListings(
                            entry.getValue(),
                            context
                    )
            );
        }

        return filtered;
    }

    private static List<Pair<VillagerTrades.ItemListing[], Integer>> filterExperimentalWanderingTrades(
            List<Pair<VillagerTrades.ItemListing[], Integer>> source
    ) {
        List<Pair<VillagerTrades.ItemListing[], Integer>> filtered =
                new ArrayList<>(source.size());

        for (Pair<VillagerTrades.ItemListing[], Integer> entry : source) {
            filtered.add(
                    Pair.of(
                            filterListings(
                                    entry.getLeft(),
                                    "Wandering Trader"
                            ),
                            entry.getRight()
                    )
            );
        }

        return List.copyOf(filtered);
    }

    private static VillagerTrades.ItemListing[] filterListings(
            VillagerTrades.ItemListing[] source,
            String context
    ) {
        List<VillagerTrades.ItemListing> filtered =
                new ArrayList<>(source.length);

        for (VillagerTrades.ItemListing listing : source) {
            Set<Object> visited = Collections.newSetFromMap(
                    new IdentityHashMap<>()
            );

            TradeMatches matches =
                    new TradeMatches();

            collectBlacklistedItems(
                    listing,
                    visited,
                    matches
            );

            if (matches.isEmpty()) {
                filtered.add(listing);
                continue;
            }

            for (ResourceLocation tagId : matches.tags) {
                BlacklistLogReport.recordTradeTagRemoval(
                        tagId,
                        describeTrade(
                                context,
                                "#" + tagId
                        )
                );
            }

            for (ResourceLocation itemId : matches.items) {
                BlacklistLogReport.recordTradeRemoval(
                        itemId,
                        describeTrade(
                                context,
                                itemId.toString()
                        )
                );
            }
        }

        return filtered.toArray(VillagerTrades.ItemListing[]::new);
    }

    private static void collectBlacklistedItems(
            VillagerTrades.ItemListing listing,
            Set<Object> visited,
            TradeMatches matches
    ) {
        if (!visited.add(listing)) {
            return;
        }

        String name =
                listing.getClass().getSimpleName();

        collectImplicitVanillaItems(
                name,
                matches
        );

        for (Field field : listing.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            try {
                if (!field.trySetAccessible()) {
                    ItemBlacklistLogs.warn(
                            TRADE,
                            "Could not inspect trade field {}.{}",
                            listing.getClass().getName(),
                            field.getName()
                    );

                    continue;
                }

                collectBlacklistedItems(
                        field.get(listing),
                        visited,
                        matches
                );
            } catch (IllegalAccessException exception) {
                ItemBlacklistLogs.error(
                        TRADE,
                        "Failed to inspect trade field {}.{}: {}",
                        listing.getClass().getName(),
                        field.getName(),
                        exception.getMessage()
                );
            }
        }
    }

    private static void collectImplicitVanillaItems(
            String listingName,
            TradeMatches matches
    ) {
        switch (listingName) {
            case "DyedArmorForEmeralds",
                 "EnchantedItemForEmeralds",
                 "ItemsAndEmeraldsToItems",
                 "ItemsForEmeralds",
                 "TippedArrowForItemsAndEmeralds" ->
                    addIfBlacklisted(
                            Items.EMERALD,
                            matches
                    );

            case "SuspiciousStewForEmerald" -> {
                addIfBlacklisted(
                        Items.EMERALD,
                        matches
                );

                addIfBlacklisted(
                        Items.SUSPICIOUS_STEW,
                        matches
                );
            }

            case "TreasureMapForEmeralds" -> {
                addIfBlacklisted(
                        Items.EMERALD,
                        matches
                );

                addIfBlacklisted(
                        Items.COMPASS,
                        matches
                );

                addIfBlacklisted(
                        Items.FILLED_MAP,
                        matches
                );
            }

            case "EnchantBookForEmeralds" -> {
                addIfBlacklisted(
                        Items.EMERALD,
                        matches
                );

                addIfBlacklisted(
                        Items.BOOK,
                        matches
                );

                addIfBlacklisted(
                        Items.ENCHANTED_BOOK,
                        matches
                );
            }

            case "EmeraldForItems",
                 "EmeraldsForVillagerTypeItem" ->
                    addIfBlacklisted(
                            Items.EMERALD,
                            matches
                    );

            default -> {
            }
        }
    }

    private static void collectBlacklistedItems(
            Object value,
            Set<Object> visited,
            TradeMatches matches
    ) {
        switch (value) {
            case Item item ->
                    addIfBlacklisted(
                            item,
                            matches
                    );

            case ItemStack stack ->
                    addIfBlacklisted(
                            stack.getItem(),
                            matches
                    );

            case ItemCost cost ->
                    addIfBlacklisted(
                            cost.item().value(),
                            matches
                    );

            case Ingredient ingredient ->
                    collectBlacklistedIngredient(
                            ingredient,
                            matches
                    );

            case TagKey<?> tag -> {
                if (tag.isFor(Registries.ITEM)
                        && BlacklistManager.isBlacklistedTag(
                        tag.location()
                )) {
                    matches.tags.add(
                            tag.location()
                    );
                }
            }

            case VillagerTrades.ItemListing listing ->
                    collectBlacklistedItems(
                            listing,
                            visited,
                            matches
                    );

            case Optional<?> optional ->
                    optional.ifPresent(
                            object -> collectBlacklistedItems(
                                    object,
                                    visited,
                                    matches
                            )
                    );

            case Map<?, ?> map -> {
                for (Object mapValue : map.values()) {
                    collectBlacklistedItems(
                            mapValue,
                            visited,
                            matches
                    );
                }
            }

            case Iterable<?> iterable -> {
                for (Object object : iterable) {
                    collectBlacklistedItems(
                            object,
                            visited,
                            matches
                    );
                }
            }

            case Object[] array -> {
                for (Object object : array) {
                    collectBlacklistedItems(
                            object,
                            visited,
                            matches
                    );
                }
            }

            case null, default -> {
            }
        }
    }

    private static void collectBlacklistedIngredient(
            Ingredient ingredient,
            TradeMatches matches
    ) {
        ItemStack[] stacks =
                ingredient.getItems();

        if (stacks.length == 0) {
            return;
        }

        for (ItemStack stack : stacks) {
            if (!BlacklistManager.isBlacklisted(
                    stack.getItem()
            )) {
                return;
            }
        }

        for (ItemStack stack : stacks) {
            matches.items.add(
                    BuiltInRegistries.ITEM.getKey(
                            stack.getItem()
                    )
            );
        }
    }

    private static void addIfBlacklisted(
            Item item,
            TradeMatches matches
    ) {
        if (!BlacklistManager.isBlacklisted(item)) {
            return;
        }

        matches.items.add(
                BuiltInRegistries.ITEM.getKey(item)
        );
    }

    private static String describeTrade(
            String context,
            String item
    ) {
        return context + " trade using " + item;
    }

    private static String levelName(
            int level
    ) {
        return switch (level) {
            case 1 -> "Novice";
            case 2 -> "Apprentice";
            case 3 -> "Journeyman";
            case 4 -> "Expert";
            case 5 -> "Master";
            default -> "Level " + level;
        };
    }

    private static String formatName(
            String path
    ) {
        String[] parts =
                path.split("_");

        StringBuilder name =
                new StringBuilder();

        for (String part : parts) {
            if (!name.isEmpty()) {
                name.append(' ');
            }

            name.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );

            name.append(
                    part.substring(1)
            );
        }

        return name.toString();
    }

    private static Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> copyProfessionTrades(
            Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> source
    ) {
        Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> copy =
                new LinkedHashMap<>();

        for (Map.Entry<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> entry :
                source.entrySet()) {
            copy.put(
                    entry.getKey(),
                    copyTrades(entry.getValue())
            );
        }

        return copy;
    }

    private static Int2ObjectMap<VillagerTrades.ItemListing[]> copyTrades(
            Int2ObjectMap<VillagerTrades.ItemListing[]> source
    ) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> copy =
                new Int2ObjectOpenHashMap<>();

        for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> entry :
                source.int2ObjectEntrySet()) {
            copy.put(
                    entry.getIntKey(),
                    entry.getValue().clone()
            );
        }

        return copy;
    }

    private static List<Pair<VillagerTrades.ItemListing[], Integer>> copyExperimentalWanderingTrades() {
        List<Pair<VillagerTrades.ItemListing[], Integer>> copy =
                new ArrayList<>(
                        VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES.size()
                );

        for (Pair<VillagerTrades.ItemListing[], Integer> entry :
                VillagerTrades.EXPERIMENTAL_WANDERING_TRADER_TRADES) {
            copy.add(
                    Pair.of(
                            Arrays.copyOf(
                                    entry.getLeft(),
                                    entry.getLeft().length
                            ),
                            entry.getRight()
                    )
            );
        }

        return List.copyOf(copy);
    }

    private static final class TradeMatches {

        private final Set<ResourceLocation> items =
                new LinkedHashSet<>();

        private final Set<ResourceLocation> tags =
                new LinkedHashSet<>();

        private boolean isEmpty() {
            return items.isEmpty()
                    && tags.isEmpty();
        }
    }
}
