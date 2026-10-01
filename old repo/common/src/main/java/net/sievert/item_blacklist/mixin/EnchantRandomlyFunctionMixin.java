package net.sievert.item_blacklist.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

@Mixin(EnchantRandomlyFunction.class)
public abstract class EnchantRandomlyFunctionMixin {

    @ModifyVariable(
            method = "run",
            at = @At("STORE"),
            ordinal = 0
    )
    private List<Holder<Enchantment>> item_blacklist$filterPossibleEnchantments(
            List<Holder<Enchantment>> enchantments
    ) {
        return enchantments.stream()
                .filter(
                        enchantment ->
                                !BlacklistManager.isBlacklistedEnchantment(
                                        enchantment
                                )
                )
                .toList();
    }
}