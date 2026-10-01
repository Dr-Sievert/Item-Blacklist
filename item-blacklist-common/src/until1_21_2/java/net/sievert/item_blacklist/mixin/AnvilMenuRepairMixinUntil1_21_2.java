package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.StackRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.1: the anvil refuses a blacklisted repair material. AnvilMenu.createResult is the one
 * caller of Item.isValidRepairItem, so wrapping the call covers every override (tiers, elytra,
 * mace, modded items) at once; from 1.21.2 ItemStackRepairMixinSince1_21_2 takes over. Reads
 * effective(): the client's copy of the menu predicts the result, so the handler tests
 * isEmpty() first and writes nothing.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuRepairMixinUntil1_21_2 {
    @WrapOperation(method = "createResult()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;isValidRepairItem("
                    + "Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean item_blacklist$refuseBlacklistedMaterial(Item item, ItemStack repaired,
            ItemStack material, Operation<Boolean> original) {
        BlacklistSnapshot snapshot = Blacklist.effective();
        if (!snapshot.isEmpty() && StackRules.blacklisted(snapshot, material)) {
            return false;
        }
        return original.call(item, repaired, material);
    }
}
