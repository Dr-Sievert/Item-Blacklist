package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.recipes.RecipeRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A blacklisted item matches no ingredient, on the server and on clients (the effective view,
 * so a client with a synced blacklist predicts what its server allows). The check sits at
 * HEAD, before NeoForge hands a custom ingredient its own test, so custom ingredients refuse
 * too; a recipe that stays keeps its blacklisted alternatives inert. The full descriptor
 * leaves the bridge test(Object) alone.
 */
@Mixin(Ingredient.class)
public abstract class IngredientMixin {
    @Inject(method = "test(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
            cancellable = true)
    private void item_blacklist$refuseBlacklisted(ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (RecipeRules.refusesMatch(Blacklist.effective(), stack)) {
            cir.setReturnValue(false);
        }
    }
}
