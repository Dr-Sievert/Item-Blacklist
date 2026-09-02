package net.sievert.item_blacklist.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.sievert.item_blacklist.blacklist.BlacklistLogReport;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Inject(
            method = "apply*",
            at = @At("HEAD")
    )
    private void item_blacklist$filterExplicitBlacklistedTags(
            Map<ResourceLocation, JsonElement> recipes,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci
    ) {
        if (BlacklistManager.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<ResourceLocation, JsonElement>> iterator =
                recipes.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry =
                    iterator.next();

            Set<ResourceLocation> blacklistedTags =
                    new LinkedHashSet<>();

            item_blacklist$collectBlacklistedTags(
                    entry.getValue(),
                    blacklistedTags
            );

            if (blacklistedTags.isEmpty()) {
                continue;
            }

            iterator.remove();

            for (ResourceLocation tagId : blacklistedTags) {
                BlacklistLogReport.recordRecipeTagRemoval(
                        tagId,
                        entry.getKey()
                );
            }
        }
    }

    @Inject(
            method = "apply*",
            at = @At("RETURN")
    )
    private void item_blacklist$filterLoadedRecipes(
            Map<ResourceLocation, JsonElement> recipes,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci
    ) {
        if (BlacklistManager.isEmpty()) {
            return;
        }

        RecipeManager recipeManager =
                (RecipeManager) (Object) this;

        List<RecipeHolder<?>> filtered =
                new ArrayList<>();

        boolean changed = false;

        for (RecipeHolder<?> holder :
                recipeManager.getRecipes()) {
            Set<ResourceLocation> blacklistedItems =
                    item_blacklist$getBlacklistedItems(
                            holder.value()
                    );

            if (blacklistedItems.isEmpty()) {
                filtered.add(holder);
                continue;
            }

            changed = true;

            for (ResourceLocation itemId : blacklistedItems) {
                BlacklistLogReport.recordRecipeRemoval(
                        itemId,
                        holder.id()
                );
            }
        }

        if (changed) {
            recipeManager.replaceRecipes(
                    filtered
            );
        }
    }

    @Unique
    private static void item_blacklist$collectBlacklistedTags(
            JsonElement element,
            Set<ResourceLocation> blacklistedTags
    ) {
        if (element.isJsonArray()) {
            JsonArray array =
                    element.getAsJsonArray();

            for (JsonElement child : array) {
                item_blacklist$collectBlacklistedTags(
                        child,
                        blacklistedTags
                );
            }

            return;
        }

        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object =
                element.getAsJsonObject();

        JsonElement tagElement =
                object.get("tag");

        if (tagElement != null
                && tagElement.isJsonPrimitive()
                && tagElement.getAsJsonPrimitive().isString()) {
            ResourceLocation tagId =
                    ResourceLocation.tryParse(
                            tagElement.getAsString()
                    );

            if (tagId != null
                    && BlacklistManager.isBlacklistedTag(tagId)) {
                blacklistedTags.add(tagId);
            }
        }

        for (Map.Entry<String, JsonElement> entry :
                object.entrySet()) {
            item_blacklist$collectBlacklistedTags(
                    entry.getValue(),
                    blacklistedTags
            );
        }
    }

    @Unique
    private Set<ResourceLocation> item_blacklist$getBlacklistedItems(
            Recipe<?> recipe
    ) {
        Set<ResourceLocation> blacklistedItems =
                new LinkedHashSet<>();

        ItemStack result =
                recipe.getResultItem(this.registries);

        if (!result.isEmpty()
                && BlacklistManager.isBlacklisted(result)) {
            blacklistedItems.add(
                    BuiltInRegistries.ITEM.getKey(
                            result.getItem()
                    )
            );
        }

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) {
                continue;
            }

            /*
             * Ingredient#getItems is filtered by IngredientMixin so normal
             * crafting, recipe-book matching and recipe viewers cannot use
             * individual blacklisted alternatives from an otherwise-valid
             * tag ingredient.
             *
             * For recipe removal we still need the original cached choices so
             * we can distinguish "some alternatives were removed" from
             * "every alternative is blacklisted". Calling getItems first
             * initializes Ingredient's raw cache; the accessor then reads that
             * unfiltered cache directly.
             */
            ingredient.getItems();

            ItemStack[] stacks =
                    ((IngredientAccessor) (Object) ingredient)
                            .item_blacklist$getCachedItems();

            if (stacks == null || stacks.length == 0) {
                continue;
            }

            boolean fullyBlacklisted =
                    true;

            for (ItemStack stack : stacks) {
                if (!BlacklistManager.isBlacklisted(stack)) {
                    fullyBlacklisted = false;
                    break;
                }
            }

            if (!fullyBlacklisted) {
                continue;
            }

            for (ItemStack stack : stacks) {
                blacklistedItems.add(
                        BuiltInRegistries.ITEM.getKey(
                                stack.getItem()
                        )
                );
            }
        }

        return blacklistedItems;
    }
}
