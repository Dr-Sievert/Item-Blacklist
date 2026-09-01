package net.sievert.item_blacklist.integration;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.ItemStack;
import net.sievert.item_blacklist.blacklist.BlacklistManager;

import java.util.ArrayList;
import java.util.List;

public final class BlacklistJeiManager {

    private static IJeiRuntime runtime;

    private static final List<IJeiBrewingRecipe> HIDDEN_BREWING_RECIPES =
            new ArrayList<>();

    private BlacklistJeiManager() {}

    public static void setRuntime(
            IJeiRuntime jeiRuntime
    ) {
        runtime = jeiRuntime;

        filterBrewingRecipes();
    }

    public static void clearRuntime() {
        runtime = null;
        HIDDEN_BREWING_RECIPES.clear();
    }

    public static void filterBrewingRecipes() {
        if (runtime == null) {
            return;
        }

        IRecipeManager recipeManager =
                runtime.getRecipeManager();

        if (!HIDDEN_BREWING_RECIPES.isEmpty()) {
            recipeManager.unhideRecipes(
                    RecipeTypes.BREWING,
                    HIDDEN_BREWING_RECIPES
            );

            HIDDEN_BREWING_RECIPES.clear();
        }

        List<IJeiBrewingRecipe> recipes =
                recipeManager
                        .createRecipeLookup(
                                RecipeTypes.BREWING
                        )
                        .includeHidden()
                        .get()
                        .filter(
                                BlacklistJeiManager::isBlacklisted
                        )
                        .toList();

        if (recipes.isEmpty()) {
            return;
        }

        HIDDEN_BREWING_RECIPES.addAll(
                recipes
        );

        recipeManager.hideRecipes(
                RecipeTypes.BREWING,
                HIDDEN_BREWING_RECIPES
        );
    }

    private static boolean isBlacklisted(
            IJeiBrewingRecipe recipe
    ) {
        if (BlacklistManager.isBlacklisted(
                recipe.getPotionOutput()
        )) {
            return true;
        }

        for (ItemStack input :
                recipe.getPotionInputs()) {
            if (BlacklistManager.isBlacklisted(input)) {
                return true;
            }
        }

        for (ItemStack ingredient :
                recipe.getIngredients()) {
            if (BlacklistManager.isBlacklisted(ingredient)) {
                return true;
            }
        }

        return false;
    }
}