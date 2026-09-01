package net.sievert.item_blacklist.mixin;

import net.minecraft.server.MinecraftServer;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.blacklist.BlacklistLogReport;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.blacklist.BlacklistValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Unique
    private boolean item_blacklist$initialized;

    @Inject(
            method = "tickServer",
            at = @At("HEAD")
    )
    private void item_blacklist$initialize(
            BooleanSupplier hasTimeLeft,
            CallbackInfo ci
    ) {
        if (this.item_blacklist$initialized) {
            return;
        }

        this.item_blacklist$initialized = true;

        MinecraftServer server =
                (MinecraftServer) (Object) this;

        BlacklistLogReport.reset();

        BlacklistValidator.validateItems(
                ItemBlacklist.CONFIG
        );

        BlacklistValidator.validateTags(
                ItemBlacklist.CONFIG
        );

        BlacklistManager.resolve();

        server.reloadResources(
                server.getPackRepository().getSelectedIds()
        ).thenRun(
                BlacklistLogReport::flush
        );
    }
}