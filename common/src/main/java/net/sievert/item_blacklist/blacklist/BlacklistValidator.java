package net.sievert.item_blacklist.blacklist;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
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
                "item",
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

    public static void validateEnchantments(
            BlacklistConfig config,
            RegistryAccess registryAccess
    ) {
        Registry<Enchantment> enchantments =
                registryAccess.registryOrThrow(
                        Registries.ENCHANTMENT
                );

        validate(
                config.blacklistEnchantments,
                enchantments::containsKey,
                "enchantment",
                false
        );

        validate(
                config.blacklistEnchantmentTags,
                id -> isValidEnchantmentTag(
                        enchantments,
                        id
                ),
                "enchantment",
                true
        );

        config.blacklistEnchantments.forEach(
                BlacklistLogReport::recordBlacklistedEnchantment
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
                        "Ignoring unknown {} tag in blacklist: #{}",
                        type,
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

        String displayType = tag
                ? type + " tag"
                : type;

        ItemBlacklistLogs.info(
                CONFIG,
                "Validated blacklist with {} valid {} and {} unknown {}",
                entries.size(),
                pluralize(displayType, entries.size()),
                invalidCount,
                pluralize(displayType, invalidCount)
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

    private static boolean isValidEnchantmentTag(
            Registry<Enchantment> enchantments,
            ResourceLocation tagId
    ) {
        TagKey<Enchantment> tag = TagKey.create(
                Registries.ENCHANTMENT,
                tagId
        );

        return enchantments
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
