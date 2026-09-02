package net.sievert.item_blacklist.blacklist;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import net.sievert.item_blacklist.mixin.PotionBrewingAccessor;
import net.sievert.item_blacklist.mixin.PotionBrewingMixAccessor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BlacklistBrewingManager {

    private BlacklistBrewingManager() {}

    public static void filter(
            PotionBrewing potionBrewing
    ) {
        PotionBrewingAccessor accessor =
                (PotionBrewingAccessor) potionBrewing;

        accessor.item_blacklist$setPotionMixes(
                List.copyOf(
                        filterPotionMixes(
                                accessor.item_blacklist$getPotionMixes()
                        )
                )
        );

        accessor.item_blacklist$setContainerMixes(
                List.copyOf(
                        filterContainerMixes(
                                accessor.item_blacklist$getContainerMixes()
                        )
                )
        );
    }

    private static List<?> filterPotionMixes(
            List<?> mixes
    ) {
        List<Object> filtered =
                new ArrayList<>();

        for (Object mix : mixes) {
            PotionBrewingMixAccessor accessor =
                    (PotionBrewingMixAccessor) mix;

            ResourceLocation inputPotion =
                    getHolderId(
                            accessor.item_blacklist$getFrom()
                    );

            ResourceLocation outputPotion =
                    getHolderId(
                            accessor.item_blacklist$getTo()
                    );

            Ingredient ingredient =
                    accessor.item_blacklist$getIngredient();

            boolean inputBlacklisted =
                    inputPotion != null
                            && BlacklistManager.isBlacklistedPotion(
                            inputPotion
                    );

            boolean outputBlacklisted =
                    outputPotion != null
                            && BlacklistManager.isBlacklistedPotion(
                            outputPotion
                    );

            boolean ingredientBlacklisted =
                    isIngredientFullyBlacklisted(
                            ingredient
                    );

            if (!inputBlacklisted
                    && !outputBlacklisted
                    && !ingredientBlacklisted) {
                filtered.add(mix);
                continue;
            }

            String recipe =
                    describeRecipe(
                            inputPotion,
                            ingredient,
                            outputPotion
                    );

            if (outputBlacklisted) {
                BlacklistLogReport.recordBrewingRecipePotionRemoval(
                        outputPotion,
                        recipe,
                        "output"
                );
            } else if (inputBlacklisted) {
                BlacklistLogReport.recordBrewingRecipePotionRemoval(
                        inputPotion,
                        recipe,
                        "input"
                );
            } else {
                recordIngredientRemoval(
                        ingredient,
                        recipe
                );
            }
        }

        return filtered;
    }

    private static List<?> filterContainerMixes(
            List<?> mixes
    ) {
        List<Object> filtered =
                new ArrayList<>();

        for (Object mix : mixes) {
            PotionBrewingMixAccessor accessor =
                    (PotionBrewingMixAccessor) mix;

            Item input =
                    getItem(
                            accessor.item_blacklist$getFrom()
                    );

            Item output =
                    getItem(
                            accessor.item_blacklist$getTo()
                    );

            Ingredient ingredient =
                    accessor.item_blacklist$getIngredient();

            boolean inputBlacklisted =
                    input != null
                            && BlacklistManager.isBlacklisted(input);

            boolean outputBlacklisted =
                    output != null
                            && BlacklistManager.isBlacklisted(output);

            boolean ingredientBlacklisted =
                    isIngredientFullyBlacklisted(
                            ingredient
                    );

            if (!inputBlacklisted
                    && !outputBlacklisted
                    && !ingredientBlacklisted) {
                filtered.add(mix);
                continue;
            }

            ResourceLocation inputId =
                    input == null
                            ? null
                            : BuiltInRegistries.ITEM.getKey(input);

            ResourceLocation outputId =
                    output == null
                            ? null
                            : BuiltInRegistries.ITEM.getKey(output);

            String recipe =
                    describeRecipe(
                            inputId,
                            ingredient,
                            outputId
                    );

            if (outputBlacklisted) {
                recordItemRemoval(
                        outputId,
                        recipe,
                        "output"
                );
            } else if (inputBlacklisted) {
                recordItemRemoval(
                        inputId,
                        recipe,
                        "input"
                );
            } else {
                recordIngredientRemoval(
                        ingredient,
                        recipe
                );
            }
        }

        return filtered;
    }

    private static void recordIngredientRemoval(
            Ingredient ingredient,
            String recipe
    ) {
        Set<ResourceLocation> items =
                new LinkedHashSet<>();

        for (ItemStack stack : ingredient.getItems()) {
            if (!BlacklistManager.isBlacklisted(stack)) {
                continue;
            }

            items.add(
                    BuiltInRegistries.ITEM.getKey(
                            stack.getItem()
                    )
            );
        }

        for (ResourceLocation item : items) {
            recordItemRemoval(
                    item,
                    recipe,
                    "ingredient"
            );
        }
    }

    private static void recordItemRemoval(
            ResourceLocation item,
            String recipe,
            String role
    ) {
        BlacklistLogReport.recordBrewingRecipeItemRemoval(
                item,
                recipe,
                role
        );

        for (ResourceLocation tag :
                BlacklistManager.getBlacklistedTags()) {
            if (!BlacklistManager
                    .getResolvedTagItems(tag)
                    .contains(item)) {
                continue;
            }

            BlacklistLogReport.recordBrewingRecipeTagRemoval(
                    tag,
                    recipe,
                    role
            );
        }
    }

    private static boolean isIngredientFullyBlacklisted(
            Ingredient ingredient
    ) {
        ItemStack[] items =
                ingredient.getItems();

        if (items.length == 0) {
            return false;
        }

        for (ItemStack stack : items) {
            if (!BlacklistManager.isBlacklisted(stack)) {
                return false;
            }
        }

        return true;
    }

    private static String describeRecipe(
            ResourceLocation input,
            Ingredient ingredient,
            ResourceLocation output
    ) {
        return "input="
                + input
                + ", ingredient="
                + describeIngredient(ingredient)
                + ", output="
                + output;
    }

    private static String describeIngredient(
            Ingredient ingredient
    ) {
        List<ResourceLocation> ids =
                new ArrayList<>();

        for (ItemStack stack : ingredient.getItems()) {
            ids.add(
                    BuiltInRegistries.ITEM.getKey(
                            stack.getItem()
                    )
            );
        }

        return ids.toString();
    }

    private static ResourceLocation getHolderId(
            Holder<?> holder
    ) {
        return holder.unwrapKey()
                .map(ResourceKey::location)
                .orElse(null);
    }

    private static Item getItem(
            Holder<?> holder
    ) {
        Object value =
                holder.value();

        if (value instanceof Item item) {
            return item;
        }

        return null;
    }
}