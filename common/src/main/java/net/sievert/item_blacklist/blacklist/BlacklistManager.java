package net.sievert.item_blacklist.blacklist;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.CONFIG;

public final class BlacklistManager {

    private static final Set<ResourceLocation> RESOLVED_BLACKLIST =
            new LinkedHashSet<>();

    private static final Set<ResourceLocation> BLACKLISTED_TAGS =
            new LinkedHashSet<>();

    private static final Map<ResourceLocation, Set<ResourceLocation>> RESOLVED_TAG_ITEMS =
            new LinkedHashMap<>();

    private static final Set<ResourceLocation> SYNCED_BLACKLIST =
            new LinkedHashSet<>();

    private static boolean syncedBlacklistActive;

    private BlacklistManager() {}

    public static void resolve() {
        RESOLVED_BLACKLIST.clear();
        BLACKLISTED_TAGS.clear();
        RESOLVED_TAG_ITEMS.clear();

        if (ItemBlacklist.CONFIG == null) {
            return;
        }

        RESOLVED_BLACKLIST.addAll(
                ItemBlacklist.CONFIG.blacklist
        );

        BLACKLISTED_TAGS.addAll(
                ItemBlacklist.CONFIG.blacklistTags
        );

        for (ResourceLocation tagId : BLACKLISTED_TAGS) {
            TagKey<Item> tag = TagKey.create(
                    BuiltInRegistries.ITEM.key(),
                    tagId
            );

            Set<ResourceLocation> resolvedItems =
                    new LinkedHashSet<>();

            BuiltInRegistries.ITEM
                    .getTag(tag)
                    .ifPresent(items -> {
                        for (var holder : items) {
                            ResourceLocation itemId =
                                    BuiltInRegistries.ITEM.getKey(
                                            holder.value()
                                    );

                            resolvedItems.add(itemId);
                            RESOLVED_BLACKLIST.add(itemId);
                        }
                    });

            RESOLVED_TAG_ITEMS.put(
                    tagId,
                    Set.copyOf(resolvedItems)
            );
        }

        ItemBlacklistLogs.info(
                CONFIG,
                "Resolved blacklist to {} unique {}",
                RESOLVED_BLACKLIST.size(),
                RESOLVED_BLACKLIST.size() == 1
                        ? "item"
                        : "items"
        );

        BlacklistTradeManager.filter();
    }

    public static void setSyncedBlacklist(
            Collection<ResourceLocation> items
    ) {
        SYNCED_BLACKLIST.clear();
        SYNCED_BLACKLIST.addAll(items);
        syncedBlacklistActive = true;
    }

    public static void clearSyncedBlacklist() {
        SYNCED_BLACKLIST.clear();
        syncedBlacklistActive = false;
    }

    public static boolean isEmpty() {
        if (syncedBlacklistActive) {
            return SYNCED_BLACKLIST.isEmpty();
        }

        return RESOLVED_BLACKLIST.isEmpty()
                && BLACKLISTED_TAGS.isEmpty();
    }

    public static boolean isResolvedBlacklisted(
            ItemStack stack
    ) {
        return isResolvedBlacklisted(
                stack.getItem()
        );
    }

    public static boolean isResolvedBlacklisted(
            Item item
    ) {
        return isResolvedBlacklisted(
                BuiltInRegistries.ITEM.getKey(item)
        );
    }

    public static boolean isResolvedBlacklisted(
            ResourceLocation itemId
    ) {
        if (syncedBlacklistActive) {
            return SYNCED_BLACKLIST.contains(itemId);
        }

        return RESOLVED_BLACKLIST.contains(itemId);
    }

    public static boolean isBlacklisted(
            ItemStack stack
    ) {
        return isResolvedBlacklisted(stack);
    }

    public static boolean isBlacklisted(
            Item item
    ) {
        return isResolvedBlacklisted(item);
    }

    public static boolean isBlacklisted(
            ResourceLocation itemId
    ) {
        return isResolvedBlacklisted(itemId);
    }

    public static boolean isBlacklistedBlock(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        Block block = state.getBlock();

        if (isResolvedBlacklisted(block.asItem())) {
            return true;
        }

        ItemStack cloneStack = block.getCloneItemStack(
                level,
                pos,
                state
        );

        return !cloneStack.isEmpty()
                && isResolvedBlacklisted(cloneStack);
    }

    public static boolean isBlacklistedTag(
            ResourceLocation tagId
    ) {
        return BLACKLISTED_TAGS.contains(tagId);
    }

    public static boolean isBlacklistedTag(
            TagKey<?> tag
    ) {
        return isBlacklistedTag(
                tag.location()
        );
    }

    public static Set<ResourceLocation> getResolvedTagItems(
            ResourceLocation tagId
    ) {
        return RESOLVED_TAG_ITEMS.getOrDefault(
                tagId,
                Set.of()
        );
    }

    public static Set<ResourceLocation> getResolvedBlacklist() {
        return Set.copyOf(
                RESOLVED_BLACKLIST
        );
    }

    public static Set<ResourceLocation> getBlacklistedTags() {
        return Set.copyOf(
                BLACKLISTED_TAGS
        );
    }
}