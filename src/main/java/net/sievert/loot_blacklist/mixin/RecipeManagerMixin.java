package net.sievert.loot_blacklist.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import net.sievert.loot_blacklist.LootBlacklist;
import net.sievert.loot_blacklist.LootBlacklistConfig;
import static net.sievert.loot_blacklist.LootBlacklistLogger.*;
import static net.sievert.loot_blacklist.LootBlacklistLogger.Group.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V",
            at = @At("HEAD")
    )
    private void loot_blacklist$filterBlacklistedRecipes(
            Map<Identifier, JsonElement> map,
            ResourceManager resourceManager,
            Profiler profiler,
            CallbackInfo ci) {

        LootBlacklistConfig config = LootBlacklist.CONFIG;
        if (config == null || config.blacklist.isEmpty()) {
            info(RECIPE, "No blacklist config present. Skipping recipe filter.");
            return;
        }

        Set<String> blacklistIds = new HashSet<>();
        config.blacklist.forEach(id -> blacklistIds.add(id.toString()));

        List<Identifier> toRemove = new ArrayList<>();
        int total = 0;

        for (Map.Entry<Identifier, JsonElement> entry : map.entrySet()) {
            total++;
            Identifier recipeId = entry.getKey();
            JsonElement json = entry.getValue();
            if (jsonContainsBlacklistedId(json, blacklistIds)) {
                toRemove.add(recipeId);
            }
        }

        // Actually remove the blacklisted recipes
        toRemove.forEach(map::remove);

        int removed = toRemove.size();
        String label = pluralize(removed, "recipe", "recipes");
        info(RECIPE, "Recipe blacklist applied: " + removed + " of " + total + " " + label + " removed.");
        // Let vanilla continue with the filtered map!
    }

    // Recursively scans JSON for any blacklisted item ID or tag.
    @Unique
    private static boolean jsonContainsBlacklistedId(JsonElement json, Set<String> blacklistIds) {
        if (json == null || json.isJsonNull()) return false;

        if (json.isJsonPrimitive()) {
            if (json.getAsJsonPrimitive().isString()) {
                String value = json.getAsString();
                return blacklistIds.contains(value);
            }
            return false;
        }

        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) {
                if (jsonContainsBlacklistedId(e, blacklistIds)) return true;
            }
        }

        if (json.isJsonObject()) {
            JsonObject obj = json.getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                if (jsonContainsBlacklistedId(e.getValue(), blacklistIds)) return true;
            }
        }

        return false;
    }
}
