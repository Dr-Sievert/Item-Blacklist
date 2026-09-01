package net.sievert.item_blacklist.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemMixin {

    @Inject(
            method = "isValidRepairItem",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$preventBlacklistedRepairIngredient(
            ItemStack stack,
            ItemStack repairCandidate,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BlacklistManager.isBlacklisted(repairCandidate)) {
            cir.setReturnValue(false);
        }
    }
}