package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.enchantments.EnchantmentRules;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.providers.SingleEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A minecraft:single enchantment provider whose enchantment is blacklisted adds nothing: raid
 * and spawn crossbows, raid vindicators, the enderman's silk-touch tool and datapack providers
 * all end in this call. Cancelled at HEAD, as the old mod did, so the level is not sampled
 * either. Reads the server's view; structure pillagers are equipped on worldgen workers, where
 * the hook only reads the snapshot.
 */
@Mixin(SingleEnchantment.class)
public abstract class SingleEnchantmentMixin {
    @Inject(method = "enchant(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/enchantment/ItemEnchantments$Mutable;"
            + "Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/DifficultyInstance;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void item_blacklist$skipBlacklisted(ItemStack stack,
            ItemEnchantments.Mutable enchantments, RandomSource random,
            DifficultyInstance difficulty, CallbackInfo ci) {
        BlacklistSnapshot snapshot = Blacklist.server();
        // The record's own accessor through a cast: no shadow member to remap.
        SingleEnchantment provider = (SingleEnchantment) (Object) this;
        if (!EnchantmentRules.allowed(snapshot, provider.enchantment())) {
            ci.cancel();
        }
    }
}
