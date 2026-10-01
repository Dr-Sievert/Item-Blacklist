package net.sievert.item_blacklist.mixin;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.SingleEnchantment;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SingleEnchantment.class)
public abstract class SingleEnchantmentMixin {

    @Shadow
    public abstract Holder<Enchantment> enchantment();

    @Inject(
            method = "enchant",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$preventBlacklistedEnchantment(
            ItemStack stack,
            ItemEnchantments.Mutable enchantments,
            RandomSource random,
            DifficultyInstance difficulty,
            CallbackInfo ci
    ) {
        if (BlacklistManager.isBlacklistedEnchantment(
                this.enchantment()
        )) {
            ci.cancel();
        }
    }
}
