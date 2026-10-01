package net.sievert.item_blacklist.integration.jei.line;

import java.util.Collection;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.IRecipeLookup;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * JeiRecipes below 1.21.4: JEI 19, whose recipe types are RecipeType. The source names neither
 * RecipeType nor IRecipeType; javac takes the type from RecipeTypes, so the text is the same as
 * the backend from 1.21.4 and only the compile differs.
 */
public final class JeiRecipesUntil1_21_4 implements JeiRecipes.Backend {
    @Override
    public List<IJeiBrewingRecipe> brewing(IJeiRuntime runtime, boolean includeHidden) {
        IRecipeLookup<IJeiBrewingRecipe> lookup =
                runtime.getRecipeManager().createRecipeLookup(RecipeTypes.BREWING);
        return (includeHidden ? lookup.includeHidden() : lookup).get().toList();
    }

    @Override
    public List<IJeiAnvilRecipe> anvil(IJeiRuntime runtime, boolean includeHidden) {
        IRecipeLookup<IJeiAnvilRecipe> lookup =
                runtime.getRecipeManager().createRecipeLookup(RecipeTypes.ANVIL);
        return (includeHidden ? lookup.includeHidden() : lookup).get().toList();
    }

    @Override
    public void hideBrewing(IJeiRuntime runtime, Collection<IJeiBrewingRecipe> recipes) {
        runtime.getRecipeManager().hideRecipes(RecipeTypes.BREWING, recipes);
    }

    @Override
    public void unhideBrewing(IJeiRuntime runtime, Collection<IJeiBrewingRecipe> recipes) {
        runtime.getRecipeManager().unhideRecipes(RecipeTypes.BREWING, recipes);
    }

    @Override
    public void hideAnvil(IJeiRuntime runtime, Collection<IJeiAnvilRecipe> recipes) {
        runtime.getRecipeManager().hideRecipes(RecipeTypes.ANVIL, recipes);
    }

    @Override
    public void unhideAnvil(IJeiRuntime runtime, Collection<IJeiAnvilRecipe> recipes) {
        runtime.getRecipeManager().unhideRecipes(RecipeTypes.ANVIL, recipes);
    }
}
