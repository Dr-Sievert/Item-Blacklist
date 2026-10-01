package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Lifecycle;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every completed reloadResources, the forced start reload, /reload, /datapack and another
 * mod's call alike, reaches Lifecycle.afterReload on the server thread, failed ones too. One
 * hook for both loaders: Fabric's end-of-reload event runs inline or queued, and NeoForge has
 * none. The method's future is returned unchanged.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "reloadResources(Ljava/util/Collection;)"
            + "Ljava/util/concurrent/CompletableFuture;",
            at = @At("RETURN"))
    private void item_blacklist$afterReload(Collection<String> ids,
            CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        // Called on the server thread the method has already waited for the reload, so this
        // runs at once; from another thread it is handed to the server thread.
        cir.getReturnValue().whenComplete((ignored, failure) -> {
            if (server.isSameThread()) {
                Lifecycle.afterReload(server, failure);
            } else {
                server.execute(() -> Lifecycle.afterReload(server, failure));
            }
        });
    }
}
