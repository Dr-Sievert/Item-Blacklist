package net.sievert.loot_blacklist.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonElement;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipeType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import net.sievert.loot_blacklist.LootBlacklist;
import net.sievert.loot_blacklist.LootBlacklistConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

import static net.sievert.loot_blacklist.LootBlacklistLogger.*;
import static net.sievert.loot_blacklist.LootBlacklistLogger.Group.*;

/**
 * Mixin for {@link RecipeManager}.
 * Filters recipes after they are loaded, removing
 * any that match blacklist criteria.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    /**
     * Injects after recipes are applied, filtering out
     * blacklisted recipes by ID, output, or ingredients.
     */
    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("RETURN")
    )
    private void loot_blacklist$filterFinal(
            java.util.Map<Identifier, JsonElement> map,
            ResourceManager resourceManager,
            Profiler profiler,
            CallbackInfo ci
    ) {
        LootBlacklistConfig config = LootBlacklist.CONFIG;
        if (config == null || config.blacklist.isEmpty()) {
            info(RECIPE, "No blacklist config present. Skipping recipe filter.");
            return;
        }

        RecipeManager rm = (RecipeManager)(Object)this;
        RecipeManagerAccessor acc = (RecipeManagerAccessor)rm;

        Set<Identifier> blacklist = config.blacklist;
        int totalBefore = acc.getRecipesById().size();

        var idBuilder = ImmutableMap.<Identifier, RecipeEntry<?>>builder();
        acc.getRecipesById().forEach((id, entry) -> {
            if (shouldKeep(entry, blacklist, rm)) {
                idBuilder.put(id, entry);
            }
        });

        var typeBuilder = ImmutableMultimap.<RecipeType<?>, RecipeEntry<?>>builder();
        acc.getRecipesByType().forEach((type, entry) -> {
            if (shouldKeep(entry, blacklist, rm)) {
                typeBuilder.put(type, entry);
            }
        });

        acc.setRecipesById(idBuilder.build());
        acc.setRecipesByType(typeBuilder.build());

        int totalAfter = acc.getRecipesById().size();
        int removed = totalBefore - totalAfter;

        if (removed > 0) {
            info(RECIPE, "Recipe blacklist: " +
                    removed + " " + pluralize(removed, "recipe", "recipes") + " removed");
        } else {
            info(RECIPE, "No blacklisted recipes found.");
        }
    }

    /**
     * Returns true if this recipe should be kept.
     * Recipes can be blacklisted by ID, output item, or ingredients.
     */
    @Unique
    private static boolean shouldKeep(RecipeEntry<?> entry, Set<Identifier> blacklist, RecipeManager rm) {
        Recipe<?> recipe = entry.value();

        if (blacklist.contains(entry.id())) return false;

        RegistryWrapper.WrapperLookup lookup = ((RecipeManagerAccessor)rm).loot_blacklist$getRegistryLookup();
        ItemStack output = recipe.getResult(lookup);
        if (!output.isEmpty() && blacklist.contains(Registries.ITEM.getId(output.getItem()))) {
            return false;
        }

        for (Ingredient ing : recipe.getIngredients()) {
            for (ItemStack stack : ing.getMatchingStacks()) {
                if (!stack.isEmpty() && blacklist.contains(Registries.ITEM.getId(stack.getItem()))) {
                    return false;
                }
            }
        }

        return true;
    }
}
