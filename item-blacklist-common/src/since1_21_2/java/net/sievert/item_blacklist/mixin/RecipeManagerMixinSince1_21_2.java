package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.recipes.RecipeHooks;
import net.sievert.item_blacklist.recipes.RecipeJudgeSince1_21_2;
import net.sievert.item_blacklist.recipes.RecipePass;
import java.util.List;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * From 1.21.2: the removal pass, when every tag is bound and before the property sets, the
 * stonecutter list, the recipe-book displays and every sync are built from the map, so nothing
 * the game derives or sends holds a removed recipe. The map is rebuilt with RecipeMap.create,
 * never copied: Fabric API attaches its sync lists there, and NeoForge's priority ordering, the
 * method's first statement from 1.21.4, orders the new map. The same source serves the 26.x
 * line as a twin of the same name in src/since26_1, under the one gate since1_21_2. The
 * server's constructor also calls this method, before any state exists: nothing happens there.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixinSince1_21_2 {
    @Shadow
    private RecipeMap recipes;

    @Inject(method = "finalizeRecipeLoading(Lnet/minecraft/world/flag/FeatureFlagSet;)V",
            at = @At("HEAD"))
    private void item_blacklist$removeBlacklistedRecipes(FeatureFlagSet enabledFeatures,
            CallbackInfo ci) {
        BlacklistSnapshot snapshot = RecipeHooks.passSnapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        List<RecipeHolder<?>> kept = RecipePass.filter(this.recipes.values(),
                RecipeJudgeSince1_21_2.forSnapshot(snapshot), Blacklist.recorder());
        if (kept != null) {
            this.recipes = RecipeMap.create(kept);
        }
    }
}
