package net.sievert.item_blacklist.blacklist;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;

import java.util.HashSet;
import java.util.Set;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.CONFIG;

public final class BlacklistValidator {

    private BlacklistValidator() {}

    public static void validateItems(BlacklistConfig config) {
        Set<ResourceLocation> invalidItems = new HashSet<>();

        for (ResourceLocation itemId : config.blacklist) {
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                invalidItems.add(itemId);

                ItemBlacklistLogs.warn(
                        CONFIG,
                        "Ignoring unknown item in blacklist: {}",
                        itemId
                );
            }
        }

        config.blacklist.removeAll(invalidItems);

        ItemBlacklistLogs.info(
                CONFIG,
                "Validated blacklist with {} valid {} and {} unknown {}",
                config.blacklist.size(),
                config.blacklist.size() == 1 ? "item" : "items",
                invalidItems.size(),
                invalidItems.size() == 1 ? "item" : "items"
        );
    }

    public static void validateTags(BlacklistConfig config) {
        Set<ResourceLocation> invalidTags = new HashSet<>();

        for (ResourceLocation tagId : config.blacklistTags) {
            TagKey<Item> tag = TagKey.create(
                    BuiltInRegistries.ITEM.key(),
                    tagId
            );

            if (BuiltInRegistries.ITEM.getTag(tag).isEmpty()) {
                invalidTags.add(tagId);

                ItemBlacklistLogs.warn(
                        CONFIG,
                        "Ignoring unknown item tag in blacklist: #{}",
                        tagId
                );
            }
        }

        config.blacklistTags.removeAll(invalidTags);

        ItemBlacklistLogs.info(
                CONFIG,
                "Validated blacklist with {} valid {} and {} unknown {}",
                config.blacklistTags.size(),
                config.blacklistTags.size() == 1 ? "tag" : "tags",
                invalidTags.size(),
                invalidTags.size() == 1 ? "tag" : "tags"
        );
    }
}