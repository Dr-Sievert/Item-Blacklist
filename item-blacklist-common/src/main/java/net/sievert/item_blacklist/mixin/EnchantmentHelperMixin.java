package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.enchantments.EnchantmentRules;
import java.util.stream.Stream;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps blacklisted enchantments out of every selectEnchantment call: the enchanting table,
 * enchant_with_levels, the by_cost providers, mob spawn equipment and any other mod's call all
 * pass their candidates through it. And returns the plain book instead of an enchanted book
 * without an enchantment when nothing allowed was left to apply. Reads the server's view: both
 * paths act on the logical server only, and on worldgen workers they only read the snapshot.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
    @ModifyVariable(method = "selectEnchantment(Lnet/minecraft/util/RandomSource;"
            + "Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)Ljava/util/List;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private static Stream<Holder<Enchantment>> item_blacklist$dropBlacklisted(
            Stream<Holder<Enchantment>> candidates) {
        BlacklistSnapshot snapshot = Blacklist.server();
        // The filtered stream replaces the argument; the method consumes it once, as before.
        return EnchantmentRules.allowedStream(snapshot, candidates);
    }

    // A wrap, not a hook at RETURN: the method reassigns its stack parameter to a new enchanted
    // book, so at RETURN the book that came in is lost.
    @WrapMethod(method = "enchantItem(Lnet/minecraft/util/RandomSource;"
            + "Lnet/minecraft/world/item/ItemStack;ILjava/util/stream/Stream;)"
            + "Lnet/minecraft/world/item/ItemStack;")
    private static ItemStack item_blacklist$keepPlainBook(RandomSource random, ItemStack stack,
            int cost, Stream<Holder<Enchantment>> candidates, Operation<ItemStack> original) {
        ItemStack result = original.call(random, stack, cost, candidates);
        BlacklistSnapshot snapshot = Blacklist.server();
        return EnchantmentRules.keepPlainBook(snapshot, stack, result);
    }
}
