package net.sievert.item_blacklist.integration.jei.line;

import java.util.Collection;
import java.util.List;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * JEI's brewing and anvil categories on the 26.x line: JEI 29 and 30 take IRecipeType in one
 * form, so the calls are direct. The 1.21.x twin is a facade, since the recipe types changed
 * at 1.21.4 there. Called by JeiBridge only.
 */
public final class JeiRecipes {
    private JeiRecipes() {
    }

    /** Every brewing recipe, hidden ones included. */
    public static List<IJeiBrewingRecipe> brewing(IJeiRuntime runtime) {
        return runtime.getRecipeManager().createRecipeLookup(RecipeTypes.BREWING)
                .includeHidden().get().toList();
    }

    /** The brewing recipes JEI shows now. */
    public static List<IJeiBrewingRecipe> visibleBrewing(IJeiRuntime runtime) {
        return runtime.getRecipeManager().createRecipeLookup(RecipeTypes.BREWING).get().toList();
    }

    /** Every anvil recipe, hidden ones included. */
    public static List<IJeiAnvilRecipe> anvil(IJeiRuntime runtime) {
        return runtime.getRecipeManager().createRecipeLookup(RecipeTypes.ANVIL)
                .includeHidden().get().toList();
    }

    /** The anvil recipes JEI shows now. */
    public static List<IJeiAnvilRecipe> visibleAnvil(IJeiRuntime runtime) {
        return runtime.getRecipeManager().createRecipeLookup(RecipeTypes.ANVIL).get().toList();
    }

    /** Hides these brewing recipes in JEI. */
    public static void hideBrewing(IJeiRuntime runtime, Collection<IJeiBrewingRecipe> recipes) {
        runtime.getRecipeManager().hideRecipes(RecipeTypes.BREWING, recipes);
    }

    /** Shows these brewing recipes in JEI again. */
    public static void unhideBrewing(IJeiRuntime runtime, Collection<IJeiBrewingRecipe> recipes) {
        runtime.getRecipeManager().unhideRecipes(RecipeTypes.BREWING, recipes);
    }

    /** Hides these anvil recipes in JEI. */
    public static void hideAnvil(IJeiRuntime runtime, Collection<IJeiAnvilRecipe> recipes) {
        runtime.getRecipeManager().hideRecipes(RecipeTypes.ANVIL, recipes);
    }

    /** Shows these anvil recipes in JEI again. */
    public static void unhideAnvil(IJeiRuntime runtime, Collection<IJeiAnvilRecipe> recipes) {
        runtime.getRecipeManager().unhideRecipes(RecipeTypes.ANVIL, recipes);
    }
}
