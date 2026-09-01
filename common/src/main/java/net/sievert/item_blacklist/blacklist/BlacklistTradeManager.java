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
import java.util.Locale;
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

            for (Map.Entry<ResourceLocation, Set<TradeDirection>> entry :
                    matches.tags.entrySet()) {
                for (TradeDirection direction : entry.getValue()) {
                    BlacklistLogReport.recordTradeTagRemoval(
                            entry.getKey(),
                            describeTrade(
                                    context,
                                    direction,
                                    "#" + entry.getKey()
                            )
                    );
                }
            }

            for (Map.Entry<ResourceLocation, Set<TradeDirection>> entry :
                    matches.items.entrySet()) {
                for (TradeDirection direction : entry.getValue()) {
                    BlacklistLogReport.recordTradeRemoval(
                            entry.getKey(),
                            describeTrade(
                                    context,
                                    direction,
                                    entry.getKey().toString()
                            )
                    );
                }
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

        collectDynamicVanillaItems(
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
                        matches,
                        getFieldDirection(
                                name,
                                field.getName()
                        )
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

    private static void collectDynamicVanillaItems(
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
                            TradeDirection.BUYING,
                            matches
                    );

            case "SuspiciousStewForEmerald" -> {
                addIfBlacklisted(
                        Items.EMERALD,
                        TradeDirection.BUYING,
                        matches
                );

                addIfBlacklisted(
                        Items.SUSPICIOUS_STEW,
                        TradeDirection.SELLING,
                        matches
                );
            }

            case "TreasureMapForEmeralds" -> {
                addIfBlacklisted(
                        Items.EMERALD,
                        TradeDirection.BUYING,
                        matches
                );

                addIfBlacklisted(
                        Items.COMPASS,
                        TradeDirection.BUYING,
                        matches
                );

                addIfBlacklisted(
                        Items.FILLED_MAP,
                        TradeDirection.SELLING,
                        matches
                );
            }

            case "EnchantBookForEmeralds" -> {
                addIfBlacklisted(
                        Items.EMERALD,
                        TradeDirection.BUYING,
                        matches
                );

                addIfBlacklisted(
                        Items.BOOK,
                        TradeDirection.BUYING,
                        matches
                );

                addIfBlacklisted(
                        Items.ENCHANTED_BOOK,
                        TradeDirection.SELLING,
                        matches
                );
            }

            case "EmeraldForItems",
                 "EmeraldsForVillagerTypeItem" ->
                    addIfBlacklisted(
                            Items.EMERALD,
                            TradeDirection.SELLING,
                            matches
                    );

            default -> {
            }
        }
    }

    private static TradeDirection getFieldDirection(
            String listingName,
            String fieldName
    ) {
        return switch (listingName) {
            case "DyedArmorForEmeralds" ->
                    fieldName.equals("item")
                            ? TradeDirection.SELLING
                            : TradeDirection.UNKNOWN;

            case "EmeraldForItems" ->
                    fieldName.equals("itemStack")
                            ? TradeDirection.BUYING
                            : TradeDirection.UNKNOWN;

            case "EmeraldsForVillagerTypeItem" ->
                    fieldName.equals("trades")
                            ? TradeDirection.BUYING
                            : TradeDirection.UNKNOWN;

            case "EnchantedItemForEmeralds",
                 "ItemsForEmeralds" ->
                    fieldName.equals("itemStack")
                            ? TradeDirection.SELLING
                            : TradeDirection.UNKNOWN;

            case "ItemsAndEmeraldsToItems", "TippedArrowForItemsAndEmeralds" -> {
                if (fieldName.equals("fromItem")) {
                    yield TradeDirection.BUYING;
                }

                if (fieldName.equals("toItem")) {
                    yield TradeDirection.SELLING;
                }

                yield TradeDirection.UNKNOWN;
            }

            default ->
                    inferDirection(fieldName);
        };
    }

    private static TradeDirection inferDirection(
            String fieldName
    ) {
        String name =
                fieldName.toLowerCase(Locale.ROOT);

        if (name.contains("cost")
                || name.contains("input")
                || name.contains("from")
                || name.contains("buy")
                || name.contains("payment")
                || name.contains("ingredient")) {
            return TradeDirection.BUYING;
        }

        if (name.contains("result")
                || name.contains("output")
                || name.contains("to")
                || name.contains("sell")) {
            return TradeDirection.SELLING;
        }

        return TradeDirection.UNKNOWN;
    }

    private static void collectBlacklistedItems(
            Object value,
            Set<Object> visited,
            TradeMatches matches,
            TradeDirection direction
    ) {
        switch (value) {
            case Item item ->
                    addIfBlacklisted(
                            item,
                            direction,
                            matches
                    );

            case ItemStack stack ->
                    addIfBlacklisted(
                            stack.getItem(),
                            direction,
                            matches
                    );

            case ItemCost cost ->
                    addIfBlacklisted(
                            cost.item().value(),
                            direction,
                            matches
                    );

            case Ingredient ingredient -> {
                for (ItemStack stack : ingredient.getItems()) {
                    addIfBlacklisted(
                            stack.getItem(),
                            direction,
                            matches
                    );
                }
            }

            case TagKey<?> tag -> {
                if (tag.isFor(Registries.ITEM)
                        && BlacklistManager.isBlacklistedTag(
                        tag.location()
                )) {
                    matches.tags
                            .computeIfAbsent(
                                    tag.location(),
                                    ignored -> new LinkedHashSet<>()
                            )
                            .add(direction);
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
                                    matches,
                                    direction
                            )
                    );

            case Map<?, ?> map -> {
                for (Object mapValue : map.values()) {
                    collectBlacklistedItems(
                            mapValue,
                            visited,
                            matches,
                            direction
                    );
                }
            }

            case Iterable<?> iterable -> {
                for (Object object : iterable) {
                    collectBlacklistedItems(
                            object,
                            visited,
                            matches,
                            direction
                    );
                }
            }

            case Object[] array -> {
                for (Object object : array) {
                    collectBlacklistedItems(
                            object,
                            visited,
                            matches,
                            direction
                    );
                }
            }

            case null, default -> {
            }
        }
    }

    private static void addIfBlacklisted(
            Item item,
            TradeDirection direction,
            TradeMatches matches
    ) {
        if (!BlacklistManager.isBlacklisted(item)) {
            return;
        }

        ResourceLocation itemId =
                BuiltInRegistries.ITEM.getKey(item);

        matches.items
                .computeIfAbsent(
                        itemId,
                        ignored -> new LinkedHashSet<>()
                )
                .add(direction);
    }

    private static String describeTrade(
            String context,
            TradeDirection direction,
            String item
    ) {
        return switch (direction) {
            case BUYING ->
                    context + " buying " + item;

            case SELLING ->
                    context + " selling " + item;

            case UNKNOWN ->
                    context + " trading " + item;
        };
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

    private enum TradeDirection {
        BUYING,
        SELLING,
        UNKNOWN
    }

    private static final class TradeMatches {

        private final Map<ResourceLocation, Set<TradeDirection>> items =
                new LinkedHashMap<>();

        private final Map<ResourceLocation, Set<TradeDirection>> tags =
                new LinkedHashMap<>();

        private boolean isEmpty() {
            return items.isEmpty()
                    && tags.isEmpty();
        }
    }
}