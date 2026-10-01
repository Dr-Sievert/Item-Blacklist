package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.recipes.RecipeRules;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1's recipe picker: a blacklisted item is never counted toward a recipe, so a shapeless
 * recipe matched through the stacked contents (not through Ingredient.test) and the recipe
 * book's auto-fill refuse it too. The one-argument form, accountSimpleStack and CraftingInput
 * all funnel into this method. From 1.21.2 the class is StackedItemContents
 * (StackedItemContentsMixinSince1_21_2).
 */
@Mixin(StackedContents.class)
public abstract class StackedContentsMixinUntil1_21_2 {
    @Inject(method = "accountStack(Lnet/minecraft/world/item/ItemStack;I)V", at = @At("HEAD"),
            cancellable = true)
    private void item_blacklist$skipBlacklisted(ItemStack stack, int maxCount, CallbackInfo ci) {
        if (RecipeRules.refusesMatch(Blacklist.effective(), stack)) {
            ci.cancel();
        }
    }
}
