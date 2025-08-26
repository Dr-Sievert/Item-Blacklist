package net.sievert.item_blacklist.mixin;

import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.village.TradeOffers;
import net.minecraft.village.VillagerProfession;
import net.sievert.item_blacklist.BlacklistVillagerTrades;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;
import java.util.function.Consumer;

/**
 * Mixin for Fabric's TradeOfferInternals.
 * Wraps villager and wandering trader trade registration
 * so blacklisted trades are filtered out with proper logging.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.object.builder.TradeOfferInternals", remap = false)
public abstract class TradeOfferInternalsMixin {
    @Unique
    private static final ThreadLocal<VillagerProfession> CAPTURED_PROFESSION = new ThreadLocal<>();

    /**
     * Capture profession before registerOffers is invoked.
     */
    @Inject(method = "registerVillagerOffers", at = @At("HEAD"))
    private static void item_blacklist$captureProfession(
            VillagerProfession profession, int level,
            TradeOfferHelper.VillagerOffersAdder factory,
            CallbackInfo ci
    ) {
        CAPTURED_PROFESSION.set(profession);
    }

    /**
     * Wrap villager trade registration.
     */
    @ModifyArgs(
            method = "registerVillagerOffers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/fabric/impl/object/builder/TradeOfferInternals;registerOffers" +
                            "(Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;ILjava/util/function/Consumer;)V"
            )
    )
    private static void item_blacklist$wrapVillagerOffers(Args args) {
        VillagerProfession profession = CAPTURED_PROFESSION.get();
        int level = args.get(1);
        Consumer<List<TradeOffers.Factory>> original = args.get(2);

        Consumer<List<TradeOffers.Factory>> wrapped = list -> {
            original.accept(list);

            int before = list.size();
            list.removeIf(f -> !BlacklistVillagerTrades.shouldKeepFactory(f, profession, level));
            int removed = before - list.size();

            if (removed > 0) {
                BlacklistVillagerTrades.incrementFabricRemoved();
            }
        };

        args.set(2, wrapped);
    }

    /**
     * Wrap wandering trader trade registration.
     */
    @ModifyArgs(
            method = "registerWanderingTraderOffers",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/fabric/impl/object/builder/TradeOfferInternals;registerOffers" +
                            "(Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;ILjava/util/function/Consumer;)V"
            )
    )
    private static void item_blacklist$wrapWanderingOffers(Args args) {
        int level = args.get(1);
        Consumer<List<TradeOffers.Factory>> original = args.get(2);

        Consumer<List<TradeOffers.Factory>> wrapped = list -> {
            original.accept(list);

            int before = list.size();
            list.removeIf(f -> !BlacklistVillagerTrades.shouldKeepFactory(f, null, level));
            int removed = before - list.size();

            if (removed > 0) {
                BlacklistVillagerTrades.incrementFabricRemoved();
            }
        };

        args.set(2, wrapped);
    }
}
