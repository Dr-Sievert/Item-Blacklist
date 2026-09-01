package net.sievert.item_blacklist.mixin;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Redirect(
            method = "createResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/ItemEnchantments$Mutable;set(Lnet/minecraft/core/Holder;I)V"
            )
    )
    private void item_blacklist$preventBlacklistedEnchantment(
            ItemEnchantments.Mutable enchantments,
            Holder<Enchantment> enchantment,
            int level
    ) {
        ResourceLocation enchantmentId =
                enchantment.unwrapKey()
                        .map(ResourceKey::location)
                        .orElse(null);

        if (enchantmentId != null
                && BlacklistManager.isBlacklistedEnchantment(
                enchantmentId
        )) {
            return;
        }

        enchantments.set(
                enchantment,
                level
        );
    }
}