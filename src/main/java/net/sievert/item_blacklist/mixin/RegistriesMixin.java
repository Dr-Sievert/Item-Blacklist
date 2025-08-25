package net.sievert.item_blacklist.mixin;

import net.minecraft.registry.Registries;
import net.sievert.item_blacklist.BlacklistValidator;
import net.sievert.item_blacklist.ItemBlacklist;

import static net.sievert.item_blacklist.BlacklistLogger.*;
import static net.sievert.item_blacklist.BlacklistLogger.Group.*;

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
    private static void item_blacklist$beforeFreeze(CallbackInfo ci) {
        var config = ItemBlacklist.CONFIG;
        if (config == null) return;

        var counter = new BlacklistValidator.Counter();
        var seen = new HashSet<String>();

        var modded = BlacklistValidator.validateModdedOnly(
                config.rawBlacklist,
                counter,
                seen
        );

        ItemBlacklist.moddedValidated = modded.size();
        config.blacklist.addAll(modded);

        ItemBlacklist.totalInvalid += counter.count;

        info(VALIDATION,
                "Blacklist entry validation summary: " +
                        ItemBlacklist.vanillaValidated + " " + pluralize(ItemBlacklist.vanillaValidated, "vanilla entry", "vanilla entries") + ", " +
                        ItemBlacklist.moddedValidated + " " + pluralize(ItemBlacklist.moddedValidated, "modded entry", "modded entries") + ", " +
                        config.blacklist.size() + " " + pluralize(config.blacklist.size(), "valid entry", "valid entries") + " total, " +
                        ItemBlacklist.totalInvalid + " " + pluralize(ItemBlacklist.totalInvalid, "invalid entry", "invalid entries")
        );
    }
}
