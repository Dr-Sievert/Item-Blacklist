package net.sievert.item_blacklist.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import net.sievert.item_blacklist.BlacklistConfig;
import net.sievert.item_blacklist.ItemBlacklist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static net.sievert.item_blacklist.BlacklistLogger.*;
import static net.sievert.item_blacklist.BlacklistLogger.Group.*;

/**
 * Mixin for {@link RecipeManager}.
 * Filters recipes during JSON load, removing
 * any whose JSON contains blacklisted identifiers.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    /**
     * Injects at head of RecipeManager.apply, before vanilla parses JSON,
     * and strips out any recipes whose JSON mentions blacklisted items.
     */
    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("HEAD")
    )
    private void item_blacklist$filterJsonRecipes(
            Map<Identifier, JsonElement> map,
            ResourceManager resourceManager,
            Profiler profiler,
            CallbackInfo ci
    ) {
        BlacklistConfig config = ItemBlacklist.CONFIG;
        if (config == null || config.blacklist.isEmpty()) {
            info(RECIPE, "No blacklist config present. Skipping recipe filter.");
            return;
        }

        Set<Identifier> blacklist = config.blacklist;
        int before = map.size();

        Iterator<Map.Entry<Identifier, JsonElement>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Identifier, JsonElement> entry = it.next();
            Identifier recipeId = entry.getKey();
            JsonElement json = entry.getValue();

            if (containsBlacklistedId(json, blacklist)) {
                it.remove();
                if (config.detailedRecipeLog) {
                    info(RECIPE, "Recipe " + recipeId + " removed");
                }
            }
        }

        int after = map.size();
        int removed = before - after;

        if (removed > 0) {
            info(RECIPE, "Recipe blacklist: " +
                    removed + " " + pluralize(removed, "recipe", "recipes") + " removed");
        } else {
            info(RECIPE, "No blacklisted recipes found.");
        }
    }

    /**
     * Walks JSON tree looking for string identifiers that match blacklist.
     */
    @Unique
    private static boolean containsBlacklistedId(JsonElement json, Set<Identifier> blacklist) {
        if (json == null) return false;

        if (json.isJsonPrimitive()) {
            JsonPrimitive prim = json.getAsJsonPrimitive();
            if (prim.isString()) {
                try {
                    Identifier id = Identifier.of(prim.getAsString());
                    return blacklist.contains(id);
                } catch (Exception ignored) {}
            }
        } else if (json.isJsonObject()) {
            for (var e : json.getAsJsonObject().entrySet()) {
                if (containsBlacklistedId(e.getValue(), blacklist)) return true;
            }
        } else if (json.isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray()) {
                if (containsBlacklistedId(el, blacklist)) return true;
            }
        }

        return false;
    }
}
