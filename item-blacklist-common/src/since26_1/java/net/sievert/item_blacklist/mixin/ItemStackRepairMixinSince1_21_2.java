package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * From 1.21.2 every repair check goes through ItemStack.isValidRepairItem (the REPAIRABLE
 * component): the anvil, NeoForge's createResultInternal, the wolf's armour. So a blacklisted
 * material never repairs, direct sets (elytra, mace) included. The same source serves the 26.x
 * line as a twin of the same name in src/since26_1, under the one gate since1_21_2; the method
 * has the same declaration and body on 26.1.2 and 26.2. Reads effective(), since the client's
 * anvil menu predicts the result: isEmpty() first, nothing written.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackRepairMixinSince1_21_2 {
    @Inject(method = "isValidRepairItem(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void item_blacklist$refuseBlacklistedMaterial(ItemStack material,
            CallbackInfoReturnable<Boolean> cir) {
        BlacklistSnapshot snapshot = Blacklist.effective();
        if (!snapshot.isEmpty() && StackRules.blacklisted(snapshot, material)) {
            cir.setReturnValue(false);
        }
    }
}
