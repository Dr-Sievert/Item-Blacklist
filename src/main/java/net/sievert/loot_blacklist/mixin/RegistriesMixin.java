package net.sievert.loot_blacklist.mixin;

import net.minecraft.registry.Registries;
import net.sievert.loot_blacklist.BlacklistValidator;
import net.sievert.loot_blacklist.LootBlacklist;

import static net.sievert.loot_blacklist.LootBlacklistLogger.*;
import static net.sievert.loot_blacklist.LootBlacklistLogger.Group.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;

/**
 * Mixin for {@link Registries}.
 * Validates blacklist entries before registries are frozen.
 */
@Mixin(Registries.class)
public abstract class RegistriesMixin {

    /**
     * Injects before registry freeze to validate
     * and finalize blacklist entries.
     */
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
                counter,
                seen
        );

        LootBlacklist.moddedValidated = modded.size();
        config.blacklist.addAll(modded);

        LootBlacklist.totalInvalid += counter.count;

        info(VALIDATION,
                "Blacklist entry validation summary: " +
                        LootBlacklist.vanillaValidated + " " + pluralize(LootBlacklist.vanillaValidated, "vanilla entry", "vanilla entries") + ", " +
                        LootBlacklist.moddedValidated + " " + pluralize(LootBlacklist.moddedValidated, "modded entry", "modded entries") + ", " +
                        config.blacklist.size() + " " + pluralize(config.blacklist.size(), "valid entry", "valid entries") + " total, " +
                        LootBlacklist.totalInvalid + " " + pluralize(LootBlacklist.totalInvalid, "invalid entry", "invalid entries")
        );
    }
}
