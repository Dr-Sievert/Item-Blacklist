package net.sievert.item_blacklist.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.trading.MerchantOffer;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(
        targets = "net.minecraft.world.entity.npc.VillagerTrades$TippedArrowForItemsAndEmeralds"
)
public abstract class TippedArrowForItemsAndEmeraldsMixin {

    @Inject(
            method = "getOffer",
            at = @At("HEAD"),
            cancellable = true
    )
    private void item_blacklist$preventEmptyPotionPool(
            Entity trader,
            RandomSource random,
            CallbackInfoReturnable<MerchantOffer> cir
    ) {
        boolean hasAllowedPotion =
                BuiltInRegistries.POTION
                        .holders()
                        .anyMatch(holder ->
                                item_blacklist$isAllowedPotion(holder)
                                        && !holder.value().getEffects().isEmpty()
                                        && trader.level()
                                        .potionBrewing()
                                        .isBrewablePotion(holder)
                        );

        if (!hasAllowedPotion) {
            cir.setReturnValue(null);
        }
    }

    @Redirect(
            method = "getOffer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/Registry;holders()Ljava/util/stream/Stream;"
            )
    )
    private Stream<Holder.Reference<Potion>> item_blacklist$filterPotionPool(
            Registry<Potion> registry
    ) {
        return registry.holders()
                .filter(
                        TippedArrowForItemsAndEmeraldsMixin::item_blacklist$isAllowedPotion
                );
    }

    @Unique
    private static boolean item_blacklist$isAllowedPotion(
            Holder<Potion> potion
    ) {
        return potion.unwrapKey()
                .map(key ->
                        !BlacklistManager.isBlacklistedPotion(
                                key.location()
                        )
                )
                .orElse(true);
    }
}
