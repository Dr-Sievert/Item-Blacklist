package net.sievert.item_blacklist.blacklist;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;

import java.util.Set;
import java.util.function.Predicate;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.CONFIG;

public final class BlacklistValidator {

    private BlacklistValidator() {}

    public static void validateItems(
            BlacklistConfig config
    ) {
        validate(
                config.blacklistItems,
                BuiltInRegistries.ITEM::containsKey,
                "item",
                false
        );

        config.blacklistItems.forEach(
                BlacklistLogReport::recordBlacklistedItem
        );
    }

    public static void validateTags(
            BlacklistConfig config
    ) {
        validate(
                config.blacklistTags,
                BlacklistValidator::isValidItemTag,
                "tag",
                true
        );
    }

    public static void validatePotions(
            BlacklistConfig config
    ) {
        validate(
                config.blacklistPotions,
                BuiltInRegistries.POTION::containsKey,
                "potion",
                false
        );

        config.blacklistPotions.forEach(
                BlacklistLogReport::recordBlacklistedPotion
        );
    }

    private static void validate(
            Set<ResourceLocation> entries,
            Predicate<ResourceLocation> validator,
            String type,
            boolean tag
    ) {
        int originalSize =
                entries.size();

        entries.removeIf(id -> {
            if (validator.test(id)) {
                return false;
            }

            if (tag) {
                ItemBlacklistLogs.warn(
                        CONFIG,
                        "Ignoring unknown item tag in blacklist: #{}",
                        id
                );
            } else {
                ItemBlacklistLogs.warn(
                        CONFIG,
                        "Ignoring unknown {} in blacklist: {}",
                        type,
                        id
                );
            }

            return true;
        });

        int invalidCount =
                originalSize - entries.size();

        ItemBlacklistLogs.info(
                CONFIG,
                "Validated blacklist with {} valid {} and {} unknown {}",
                entries.size(),
                pluralize(type, entries.size()),
                invalidCount,
                pluralize(type, invalidCount)
        );
    }

    private static boolean isValidItemTag(
            ResourceLocation tagId
    ) {
        TagKey<Item> tag = TagKey.create(
                BuiltInRegistries.ITEM.key(),
                tagId
        );

        return BuiltInRegistries.ITEM
                .getTag(tag)
                .isPresent();
    }

    private static String pluralize(
            String word,
            int count
    ) {
        return count == 1
                ? word
                : word + "s";
    }
}