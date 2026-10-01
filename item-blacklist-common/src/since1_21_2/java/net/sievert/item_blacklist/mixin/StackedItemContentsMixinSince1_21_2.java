package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.recipes.RecipeRules;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The recipe picker from 1.21.2: a blacklisted item is never counted toward a recipe, so a
 * shapeless recipe matched through the stacked contents (not through Ingredient.test) and the
 * recipe book's auto-fill refuse it too. CraftingInput, the inventory, the furnace, the
 * crafter and SimpleContainer all funnel into this method. The same source serves the 26.x
 * line as a twin of the same name in src/since26_1, under the one gate since1_21_2; below
 * 1.21.2 the class is StackedContents (StackedContentsMixinUntil1_21_2).
 */
@Mixin(StackedItemContents.class)
public abstract class StackedItemContentsMixinSince1_21_2 {
    @Inject(method = "accountStack(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"),
            cancellable = true)
    private void item_blacklist$skipBlacklisted(ItemStack stack, int maxCount, CallbackInfo ci) {
        if (RecipeRules.refusesMatch(Blacklist.effective(), stack)) {
            ci.cancel();
        }
    }
}
