package net.sievert.item_blacklist.blacklist;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
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

    private static final Set<ResourceLocation> BLACKLISTED_POTIONS =
            new LinkedHashSet<>();

    private static final Set<ResourceLocation> RESOLVED_ENCHANTMENTS =
            new LinkedHashSet<>();

    private static final Set<ResourceLocation> BLACKLISTED_ENCHANTMENT_TAGS =
            new LinkedHashSet<>();

    private static final Map<ResourceLocation, Set<ResourceLocation>> RESOLVED_TAG_ITEMS =
            new LinkedHashMap<>();

    private static final Set<ResourceLocation> SYNCED_BLACKLIST =
            new LinkedHashSet<>();

    private static final Set<ResourceLocation> SYNCED_POTIONS =
            new LinkedHashSet<>();

    private static final Set<ResourceLocation> SYNCED_ENCHANTMENTS =
            new LinkedHashSet<>();

    private static Registry<Enchantment> authoritativeEnchantmentRegistry;

    private static boolean remoteClient;
    private static boolean syncedBlacklistActive;

    private BlacklistManager() {}

    public static void resolve(
            RegistryAccess registryAccess
    ) {
        rebuildResolvedState(
                registryAccess
        );

        RESOLVED_ENCHANTMENTS.forEach(
                BlacklistLogReport::recordBlacklistedEnchantment
        );

        ItemBlacklistLogs.info(
                CONFIG,
                "Resolved blacklist to {} unique items, {} potions, and {} enchantments",
                RESOLVED_BLACKLIST.size(),
                BLACKLISTED_POTIONS.size(),
                RESOLVED_ENCHANTMENTS.size()
        );

        BlacklistTradeManager.filter();
    }

    private static void rebuildResolvedState(
            RegistryAccess registryAccess
    ) {
        RESOLVED_BLACKLIST.clear();
        BLACKLISTED_TAGS.clear();
        BLACKLISTED_POTIONS.clear();
        RESOLVED_ENCHANTMENTS.clear();
        BLACKLISTED_ENCHANTMENT_TAGS.clear();
        RESOLVED_TAG_ITEMS.clear();

        if (ItemBlacklist.CONFIG == null) {
            return;
        }

        RESOLVED_BLACKLIST.addAll(
                ItemBlacklist.CONFIG.blacklistItems
        );

        BLACKLISTED_TAGS.addAll(
                ItemBlacklist.CONFIG.blacklistTags
        );

        BLACKLISTED_POTIONS.addAll(
                ItemBlacklist.CONFIG.blacklistPotions
        );

        RESOLVED_ENCHANTMENTS.addAll(
                ItemBlacklist.CONFIG.blacklistEnchantments
        );

        BLACKLISTED_ENCHANTMENT_TAGS.addAll(
                ItemBlacklist.CONFIG.blacklistEnchantmentTags
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
                        for (Holder<Item> holder : items) {
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

        Registry<Enchantment> enchantmentRegistry =
                registryAccess.registryOrThrow(
                        Registries.ENCHANTMENT
                );

        authoritativeEnchantmentRegistry =
                enchantmentRegistry;

        for (ResourceLocation tagId : BLACKLISTED_ENCHANTMENT_TAGS) {
            TagKey<Enchantment> tag = TagKey.create(
                    Registries.ENCHANTMENT,
                    tagId
            );

            enchantmentRegistry
                    .getTag(tag)
                    .ifPresent(enchantments -> {
                        for (Holder<Enchantment> holder : enchantments) {
                            holder.unwrapKey()
                                    .ifPresent(key ->
                                            RESOLVED_ENCHANTMENTS.add(
                                                    key.location()
                                            )
                                    );
                        }
                    });
        }
    }

    public static void setRemoteClient(
            boolean remote
    ) {
        remoteClient = remote;

        if (!remote) {
            clearSyncedBlacklist();
        }
    }

    public static void setSyncedBlacklist(
            Collection<ResourceLocation> items,
            Collection<ResourceLocation> potions,
            Collection<ResourceLocation> enchantments
    ) {
        SYNCED_BLACKLIST.clear();
        SYNCED_BLACKLIST.addAll(items);

        SYNCED_POTIONS.clear();
        SYNCED_POTIONS.addAll(potions);

        SYNCED_ENCHANTMENTS.clear();
        SYNCED_ENCHANTMENTS.addAll(enchantments);

        syncedBlacklistActive = true;

        BlacklistTradeManager.filter();
    }

    public static void clearSyncedBlacklist() {
        SYNCED_BLACKLIST.clear();
        SYNCED_POTIONS.clear();
        SYNCED_ENCHANTMENTS.clear();

        syncedBlacklistActive = false;

        BlacklistTradeManager.filter();
    }

    public static boolean isEmpty() {
        if (remoteClient) {
            return !syncedBlacklistActive
                    || (SYNCED_BLACKLIST.isEmpty()
                    && SYNCED_POTIONS.isEmpty()
                    && SYNCED_ENCHANTMENTS.isEmpty());
        }

        return RESOLVED_BLACKLIST.isEmpty()
                && BLACKLISTED_TAGS.isEmpty()
                && BLACKLISTED_POTIONS.isEmpty()
                && RESOLVED_ENCHANTMENTS.isEmpty()
                && BLACKLISTED_ENCHANTMENT_TAGS.isEmpty();
    }

    public static boolean isBlacklisted(
            ItemStack stack
    ) {
        if (isBlacklisted(stack.getItem())) {
            return true;
        }

        if (hasBlacklistedEnchantment(stack)) {
            return true;
        }

        PotionContents contents =
                stack.get(DataComponents.POTION_CONTENTS);

        if (contents == null) {
            return false;
        }

        return contents.potion()
                .map(holder ->
                        holder.unwrapKey()
                                .map(key ->
                                        isBlacklistedPotion(
                                                key.location()
                                        )
                                )
                                .orElse(false)
                )
                .orElse(false);
    }

    public static boolean isBlacklisted(
            Item item
    ) {
        return isBlacklisted(
                BuiltInRegistries.ITEM.getKey(item)
        );
    }

    public static boolean isBlacklisted(
            ResourceLocation itemId
    ) {
        if (remoteClient) {
            return syncedBlacklistActive
                    && SYNCED_BLACKLIST.contains(itemId);
        }

        return RESOLVED_BLACKLIST.contains(itemId);
    }

    public static boolean isBlacklistedPotion(
            ResourceLocation potionId
    ) {
        if (remoteClient) {
            return syncedBlacklistActive
                    && SYNCED_POTIONS.contains(potionId);
        }

        return BLACKLISTED_POTIONS.contains(potionId);
    }

    public static boolean hasBlacklistedEnchantment(
            ItemStack stack
    ) {
        ItemEnchantments enchantments =
                stack.getOrDefault(
                        DataComponents.ENCHANTMENTS,
                        ItemEnchantments.EMPTY
                );

        if (containsBlacklistedEnchantment(enchantments)) {
            return true;
        }

        ItemEnchantments storedEnchantments =
                stack.getOrDefault(
                        DataComponents.STORED_ENCHANTMENTS,
                        ItemEnchantments.EMPTY
                );

        return containsBlacklistedEnchantment(
                storedEnchantments
        );
    }

    public static boolean isBlacklistedEnchantment(
            Holder<Enchantment> enchantment
    ) {
        return enchantment.unwrapKey()
                .map(key ->
                        isBlacklistedEnchantment(
                                key.location()
                        )
                )
                .orElse(false);
    }

    public static boolean isBlacklistedEnchantment(
            ResourceLocation enchantmentId
    ) {
        if (remoteClient) {
            return syncedBlacklistActive
                    && SYNCED_ENCHANTMENTS.contains(
                    enchantmentId
            );
        }

        return RESOLVED_ENCHANTMENTS.contains(
                enchantmentId
        );
    }

    private static boolean containsBlacklistedEnchantment(
            ItemEnchantments enchantments
    ) {
        for (Holder<Enchantment> enchantment :
                enchantments.keySet()) {
            if (isBlacklistedEnchantment(enchantment)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isBlacklistedBlock(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        Block block =
                state.getBlock();

        if (isBlacklisted(block.asItem())) {
            return true;
        }

        ItemStack cloneStack =
                block.getCloneItemStack(
                        level,
                        pos,
                        state
                );

        return !cloneStack.isEmpty()
                && isBlacklisted(cloneStack);
    }

    public static boolean isBlacklistedTag(
            ResourceLocation tagId
    ) {
        return !remoteClient
                && BLACKLISTED_TAGS.contains(tagId);
    }

    public static boolean isBlacklistedEnchantmentTag(
            ResourceLocation tagId
    ) {
        return !remoteClient
                && BLACKLISTED_ENCHANTMENT_TAGS.contains(tagId);
    }

    public static void replaceResolvedTagItems(
            Map<ResourceLocation, Set<ResourceLocation>> resolvedTagItems
    ) {
        RESOLVED_TAG_ITEMS.clear();
        RESOLVED_BLACKLIST.clear();

        if (ItemBlacklist.CONFIG != null) {
            RESOLVED_BLACKLIST.addAll(
                    ItemBlacklist.CONFIG.blacklistItems
            );
        }

        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> entry :
                resolvedTagItems.entrySet()) {
            Set<ResourceLocation> items =
                    Set.copyOf(entry.getValue());

            RESOLVED_TAG_ITEMS.put(
                    entry.getKey(),
                    items
            );

            RESOLVED_BLACKLIST.addAll(items);
        }

        BlacklistTradeManager.filter();
    }

    public static boolean isAuthoritativeEnchantmentRegistry(
            Registry<?> registry
    ) {
        return registry == authoritativeEnchantmentRegistry;
    }

    public static void replaceResolvedEnchantments(
            Collection<ResourceLocation> resolvedFromTags
    ) {
        RESOLVED_ENCHANTMENTS.clear();

        if (ItemBlacklist.CONFIG != null) {
            RESOLVED_ENCHANTMENTS.addAll(
                    ItemBlacklist.CONFIG.blacklistEnchantments
            );
        }

        RESOLVED_ENCHANTMENTS.addAll(
                resolvedFromTags
        );

        BlacklistTradeManager.filter();
    }

    public static Set<ResourceLocation> getResolvedTagItems(
            ResourceLocation tagId
    ) {
        if (remoteClient) {
            return Set.of();
        }

        return RESOLVED_TAG_ITEMS.getOrDefault(
                tagId,
                Set.of()
        );
    }

    public static Set<ResourceLocation> getResolvedBlacklist() {
        if (remoteClient) {
            return syncedBlacklistActive
                    ? Set.copyOf(SYNCED_BLACKLIST)
                    : Set.of();
        }

        return Set.copyOf(
                RESOLVED_BLACKLIST
        );
    }

    public static Set<ResourceLocation> getBlacklistedTags() {
        if (remoteClient) {
            return Set.of();
        }

        return Set.copyOf(
                BLACKLISTED_TAGS
        );
    }

    public static Set<ResourceLocation> getBlacklistedPotions() {
        if (remoteClient) {
            return syncedBlacklistActive
                    ? Set.copyOf(SYNCED_POTIONS)
                    : Set.of();
        }

        return Set.copyOf(
                BLACKLISTED_POTIONS
        );
    }

    public static Set<ResourceLocation> getBlacklistedEnchantments() {
        if (remoteClient) {
            return syncedBlacklistActive
                    ? Set.copyOf(SYNCED_ENCHANTMENTS)
                    : Set.of();
        }

        return Set.copyOf(
                RESOLVED_ENCHANTMENTS
        );
    }

}