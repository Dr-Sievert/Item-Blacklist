package net.sievert.loot_blacklist.mixin;

import net.minecraft.registry.Registries;
import net.sievert.loot_blacklist.BlacklistValidator;
import net.sievert.loot_blacklist.LootBlacklist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;

@Mixin(Registries.class)
public abstract class RegistriesMixin {

    @Inject(
            method = "bootstrap",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/registry/Registries;freezeRegistries()V"
            )
    )
    private static void loot_blacklist$beforeFreeze(CallbackInfo ci) {
        var config = LootBlacklist.CONFIG;
        if (config == null) return;

        var counter = new BlacklistValidator.Counter();
        var seen = new HashSet<String>();

        var modded = BlacklistValidator.validateModdedOnly(
                config.rawBlacklist,
                LootBlacklist.LOGGER,
                counter,
                seen
        );

        LootBlacklist.moddedValidated = modded.size();
        config.blacklist.addAll(modded);

        // Add modded invalids to existing totalInvalid (which already contains vanilla invalids)
        LootBlacklist.totalInvalid += counter.count;

        LootBlacklist.LOGGER.info(
                "Blacklist entry validation summary: valid vanilla = {}, valid modded = {}, total valid = {}, total invalid = {}",
                LootBlacklist.vanillaValidated,
                LootBlacklist.moddedValidated,
                config.blacklist.size(),
                LootBlacklist.totalInvalid
        );
    }
}
