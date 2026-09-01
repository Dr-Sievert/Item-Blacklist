package net.sievert.item_blacklist.neoforge.brew;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.brewing.BrewingRecipeRegistry;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.neoforge.mixin.PotionBrewingNeoForgeAccessor;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;

import java.util.ArrayList;
import java.util.List;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.RECIPE;

public final class NeoForgeBrewingFilter {

    private NeoForgeBrewingFilter() {}

    @SuppressWarnings("UnstableApiUsage")
    public static void filter(
            PotionBrewing potionBrewing
    ) {
        List<IBrewingRecipe> filtered =
                new ArrayList<>();

        int removed =
                0;

        for (IBrewingRecipe recipe :
                potionBrewing.getRecipes()) {
            if (!(recipe instanceof BrewingRecipe brewingRecipe)) {
                filtered.add(recipe);
                continue;
            }

            Ingredient input =
                    brewingRecipe.getInput();

            Ingredient ingredient =
                    brewingRecipe.getIngredient();

            ItemStack output =
                    brewingRecipe.getOutput();

            if (!shouldRemove(
                    input,
                    ingredient,
                    output
            )) {
                filtered.add(recipe);
                continue;
            }

            removed++;

            if (ItemBlacklist.CONFIG != null
                    && ItemBlacklist.CONFIG.detailedLog) {
                ItemBlacklistLogs.info(
                        RECIPE,
                        "Removed NeoForge brewing recipe: input={}, ingredient={}, output={}",
                        describeIngredient(input),
                        describeIngredient(ingredient),
                        describeStack(output)
                );
            }
        }

        PotionBrewingNeoForgeAccessor accessor =
                (PotionBrewingNeoForgeAccessor) potionBrewing;

        accessor.item_blacklist$setRegistry(
                new BrewingRecipeRegistry(
                        List.copyOf(filtered)
                )
        );

        if (removed > 0) {
            ItemBlacklistLogs.info(
                    RECIPE,
                    "Removed {} disabled NeoForge brewing {}",
                    removed,
                    removed == 1
                            ? "recipe"
                            : "recipes"
            );
        }
    }

    private static boolean shouldRemove(
            Ingredient input,
            Ingredient ingredient,
            ItemStack output
    ) {
        if (BlacklistManager.isBlacklisted(output)) {
            return true;
        }

        if (isFullyBlacklisted(input)) {
            return true;
        }

        return isFullyBlacklisted(ingredient);
    }

    private static boolean isFullyBlacklisted(
            Ingredient ingredient
    ) {
        ItemStack[] stacks =
                ingredient.getItems();

        if (stacks.length == 0) {
            return false;
        }

        for (ItemStack stack : stacks) {
            if (!BlacklistManager.isBlacklisted(stack)) {
                return false;
            }
        }

        return true;
    }

    private static String describeIngredient(
            Ingredient ingredient
    ) {
        ItemStack[] stacks =
                ingredient.getItems();

        List<String> entries =
                new ArrayList<>();

        for (ItemStack stack : stacks) {
            entries.add(
                    describeStack(stack)
            );
        }

        return entries.toString();
    }

    private static String describeStack(
            ItemStack stack
    ) {
        return BuiltInRegistries.ITEM
                .getKey(stack.getItem())
                .toString();
    }
}