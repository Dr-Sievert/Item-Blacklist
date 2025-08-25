package net.sievert.item_blacklist.mixin;

import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import com.google.common.collect.Multimap;

/**
 * Accessor mixin for {@link RecipeManager}.
 * Exposes private recipe maps and registry lookup for blacklist code.
 */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    /**
     * Mutator for the 'recipesById' field.
     */
    @Accessor("recipesById")
    void setRecipesById(Map<Identifier, RecipeEntry<?>> value);

    /**
     * Mutator for the 'recipesByType' field.
     */
    @Accessor("recipesByType")
    void setRecipesByType(Multimap<RecipeType<?>, RecipeEntry<?>> value);

    /**
     * Accessor for the 'recipesById' field.
     */
    @Accessor("recipesById")
    Map<Identifier, RecipeEntry<?>> getRecipesById();

    /**
     * Accessor for the 'recipesByType' field.
     */
    @Accessor("recipesByType")
    Multimap<RecipeType<?>, RecipeEntry<?>> getRecipesByType();

    /**
     * Accessor for the 'registryLookup' field.
     */
    @Accessor("registryLookup")
    RegistryWrapper.WrapperLookup item_blacklist$getRegistryLookup();
}
