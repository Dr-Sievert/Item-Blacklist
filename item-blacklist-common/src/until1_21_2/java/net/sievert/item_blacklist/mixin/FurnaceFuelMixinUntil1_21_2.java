package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.21.1: a blacklisted stack is no furnace fuel. The fuel table is one static map there,
 * which the port leaves as it is; the furnace's two fuel queries answer "no fuel" instead, so
 * the fuel slot, hoppers, the furnace menu and the lit check refuse the stack. From 1.21.2 the
 * static isFuel is gone and FuelValuesBuilderMixinSince1_21_2 filters every table as it is
 * built. Fabric API redirects the table read inside both methods, which a HEAD hook does not
 * disturb; on NeoForge the burn-time listener does the same work, and this hook answers first.
 * The hooks read the effective snapshot, since fuel checks also shape what a client predicts,
 * and never record.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceFuelMixinUntil1_21_2 {
    @Inject(method = "isFuel(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
            cancellable = true)
    private static void item_blacklist$refuseFuel(ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (StackRules.blacklisted(Blacklist.effective(), stack)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getBurnDuration(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"),
            cancellable = true)
    private void item_blacklist$noBurnTime(ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        if (StackRules.blacklisted(Blacklist.effective(), fuel)) {
            cir.setReturnValue(0);
        }
    }
}
