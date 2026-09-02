package net.sievert.item_blacklist.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.stream.Stream;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {

    @ModifyVariable(
            method = "selectEnchantment",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static Stream<Holder<Enchantment>> item_blacklist$filterPossibleEnchantments(
            Stream<Holder<Enchantment>> enchantments
    ) {
        return enchantments.filter(
                enchantment ->
                        !BlacklistManager.isBlacklistedEnchantment(
                                enchantment
                        )
        );
    }
}