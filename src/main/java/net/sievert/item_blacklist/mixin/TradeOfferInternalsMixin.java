package net.sievert.item_blacklist.mixin;

import net.minecraft.village.TradeOffers;
import net.sievert.item_blacklist.BlacklistVillagerTrades;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Consumer;

/**
 * Mixin for Fabric's TradeOfferInternals.
 * Redirects trade registration to filter trades
 * against the blacklist after Fabric adds them.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.object.builder.TradeOfferInternals", remap = false)
public abstract class TradeOfferInternalsMixin {

    /**
     * Redirects Consumer.accept to filter blacklisted trades
     * after Fabric API has registered them.
     */
    @Redirect(
            method = "registerOffers",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"
            )
    )
    private static void item_blacklist$filterTrades(
            Consumer<List<TradeOffers.Factory>> original,
            Object listObj
    ) {
        @SuppressWarnings("unchecked")
        List<TradeOffers.Factory> list = (List<TradeOffers.Factory>) listObj;

        original.accept(list);

        int before = list.size();
        list.removeIf(f -> !BlacklistVillagerTrades.shouldKeepFactory(f));
        int removed = before - list.size();

        if (removed > 0) {
            BlacklistVillagerTrades.incrementFabricRemoved();
        }
    }
}
