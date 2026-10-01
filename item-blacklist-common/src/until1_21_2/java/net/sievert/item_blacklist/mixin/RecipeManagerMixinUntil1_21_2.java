package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.recipes.RecipeHooks;
import net.sievert.item_blacklist.recipes.RecipeJsonTags;
import net.sievert.item_blacklist.recipes.RecipeManagerStateUntil1_21_2;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.JsonElement;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1: remembers the item tag ids each recipe's JSON names, since a parsed ingredient keeps
 * no tag key, and gives the pass the manager's registries and a replace that keeps the error
 * flag. The JSON is only read, never edited; the pass itself runs after the tags are bound
 * (RecipePassMixinUntil1_21_2). The handler's map is typed Map<?, JsonElement>, so the id
 * class is named by no source here; the full descriptor leaves the bridge apply(Object, ...)
 * alone.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixinUntil1_21_2 implements RecipeManagerStateUntil1_21_2 {
    @Shadow
    @Final
    private HolderLookup.Provider registries;

    @Shadow
    private boolean hasErrors;

    @Unique
    private Map<String, Set<String>> item_blacklist$tagNames = Map.of();

    @Inject(method = "apply(Ljava/util/Map;"
            + "Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void item_blacklist$collectTagNames(Map<?, JsonElement> recipes,
            ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        // At the world load no server state exists: nothing is kept, and no pass runs there.
        // isEmpty() counts configured tags, so a configured tag no datapack defined yet keeps
        // the walk on: this apply runs before the bind that may give the tag its members.
        this.item_blacklist$tagNames = !RecipeHooks.passSnapshot().isEmpty()
                ? RecipeJsonTags.collect(recipes)
                : Map.of();
    }

    @Override
    public Map<String, Set<String>> item_blacklist$takeTagNames() {
        Map<String, Set<String>> names = this.item_blacklist$tagNames;
        this.item_blacklist$tagNames = Map.of();
        return names;
    }

    @Override
    public HolderLookup.Provider item_blacklist$registries() {
        return this.registries;
    }

    @Override
    public void item_blacklist$replaceKeepingErrors(List<RecipeHolder<?>> kept) {
        boolean hadErrors = this.hasErrors;
        ((RecipeManager) (Object) this).replaceRecipes(kept);
        this.hasErrors = hadErrors;
    }
}
