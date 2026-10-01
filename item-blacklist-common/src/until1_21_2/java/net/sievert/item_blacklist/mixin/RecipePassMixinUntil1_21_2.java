package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.recipes.RecipeHooks;
import net.sievert.item_blacklist.recipes.RecipeManagerStateUntil1_21_2;
import net.sievert.item_blacklist.recipes.RecipeRemovalUntil1_21_2;
import java.util.List;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1: the removal pass, after every registry's tags are bound (TagBindMixinUntil1_21_2 has
 * published the snapshot with the tags the strip emptied by then) and before the server sends
 * the recipes to its players, so no removed recipe is ever sent. The descriptor ()V separates
 * this method from the static overload the tag hook targets. At the world load no server state
 * exists and nothing happens.
 */
@Mixin(ReloadableServerResources.class)
public abstract class RecipePassMixinUntil1_21_2 {
    @Inject(method = "updateRegistryTags()V", at = @At("RETURN"))
    private void item_blacklist$removeBlacklistedRecipes(CallbackInfo ci) {
        BlacklistSnapshot snapshot = RecipeHooks.passSnapshot();
        if (snapshot.isEmpty()) {
            return;
        }
        RecipeManager manager = ((ReloadableServerResources) (Object) this).getRecipeManager();
        List<RecipeHolder<?>> kept =
                RecipeRemovalUntil1_21_2.filter(manager, snapshot, Blacklist.recorder());
        if (kept != null) {
            ((RecipeManagerStateUntil1_21_2) manager).item_blacklist$replaceKeepingErrors(kept);
        }
    }
}
