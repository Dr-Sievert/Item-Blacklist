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
                config.blacklist,
                BuiltInRegistries.ITEM::containsKey,
                "item",
                false
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

            ItemBlacklistLogs.warn(
                    CONFIG,
                    tag
                            ? "Ignoring unknown item tag in blacklist: #{}"
                            : "Ignoring unknown item in blacklist: {}",
                    id
            );

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