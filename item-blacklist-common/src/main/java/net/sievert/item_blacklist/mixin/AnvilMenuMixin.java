package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.enchantments.EnchantmentRules;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * An anvil merge does not add or raise a blacklisted enchantment of the right input; the rest
 * of the merge, its cost included, stays vanilla's, as in the old mod. Vanilla and Fabric make
 * the call in createResult; NeoForge from 1.21.5 moves the body into createResultInternal and
 * keeps createResult as a wrapper without the call, so both names are selected and the one
 * injection is counted over both. A wrap, not a redirect, so another mod's wrapper or redirect
 * of the same call still chains. Reads the effective view: the client's preview runs it too.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @WrapOperation(method = {"createResult()V", "createResultInternal()V"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/ItemEnchantments$Mutable;"
                            + "set(Lnet/minecraft/core/Holder;I)V"))
    private void item_blacklist$skipBlacklisted(ItemEnchantments.Mutable enchantments,
            Holder<Enchantment> enchantment, int level, Operation<Void> original) {
        BlacklistSnapshot snapshot = Blacklist.effective();
        if (EnchantmentRules.allowed(snapshot, enchantment)) {
            original.call(enchantments, enchantment, level);
        }
    }
}
