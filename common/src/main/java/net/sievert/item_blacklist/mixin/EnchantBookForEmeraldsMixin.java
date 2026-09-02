package net.sievert.item_blacklist.mixin;

import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(
        targets = "net.minecraft.world.entity.npc.VillagerTrades$EnchantBookForEmeralds"
)
public abstract class EnchantBookForEmeraldsMixin {

    @Shadow
    @Final
    private TagKey<Enchantment> tradeableEnchantments;

    @Inject(
            method = "getOffer",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$preventEmptyEnchantmentTrade(
            Entity trader,
            RandomSource random,
            CallbackInfoReturnable<MerchantOffer> cir
    ) {
        Registry<Enchantment> registry =
                trader.level()
                        .registryAccess()
                        .registryOrThrow(
                                Registries.ENCHANTMENT
                        );

        boolean hasAllowedEnchantment =
                registry.getTag(
                                this.tradeableEnchantments
                        )
                        .map(enchantments ->
                                enchantments.stream()
                                        .anyMatch(enchantment ->
                                                !BlacklistManager.isBlacklistedEnchantment(
                                                        enchantment
                                                )
                                        )
                        )
                        .orElse(false);

        if (!hasAllowedEnchantment) {
            cir.setReturnValue(null);
        }
    }

    @Redirect(
            method = "getOffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/Registry;getRandomElementOf(Lnet/minecraft/tags/TagKey;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"
            )
    )
    private Optional<Holder<Enchantment>> item_blacklist$filterTradeEnchantments(
            Registry<Enchantment> registry,
            TagKey<Enchantment> tag,
            RandomSource random
    ) {
        return registry.getTag(tag)
                .flatMap(enchantments -> {
                    List<Holder<Enchantment>> allowed =
                            enchantments.stream()
                                    .filter(enchantment ->
                                            !BlacklistManager.isBlacklistedEnchantment(
                                                    enchantment
                                            )
                                    )
                                    .toList();

                    return Util.getRandomSafe(
                            allowed,
                            random
                    );
                });
    }
}
