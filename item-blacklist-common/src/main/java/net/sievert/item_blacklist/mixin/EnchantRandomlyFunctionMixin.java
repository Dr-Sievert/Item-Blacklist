package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.enchantments.EnchantmentRules;
import java.util.List;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps blacklisted enchantments out of minecraft:enchant_randomly: the candidate list is
 * filtered where run() builds it, before the random pick, so a roll never lands on one. An
 * emptied list takes vanilla's own path (a warning, the stack unchanged). Bound to the one
 * Stream.toList() call of run(), not to a local by type, so a new List local of a later release
 * cannot capture the handler. Reads the server's view: loot is rolled on the logical server.
 */
@Mixin(EnchantRandomlyFunction.class)
public abstract class EnchantRandomlyFunctionMixin {
    @ModifyExpressionValue(method = "run(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/level/storage/loot/LootContext;)"
            + "Lnet/minecraft/world/item/ItemStack;",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/stream/Stream;toList()Ljava/util/List;"))
    private List<Holder<Enchantment>> item_blacklist$dropBlacklisted(
            List<Holder<Enchantment>> candidates) {
        BlacklistSnapshot snapshot = Blacklist.server();
        return EnchantmentRules.allowedList(snapshot, candidates);
    }
}
