package net.sievert.item_blacklist.mixin;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.tag.TagManagerLoader;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.profiler.Profiler;
import net.sievert.item_blacklist.BlacklistTags;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Mixin for TagManagerLoader.
 * Used to capture the number of tag registries being loaded at the start of a reload.
 * Sets the expected registry count for global blacklist summary timing.
 */
@Mixin(TagManagerLoader.class)
public abstract class TagManagerLoaderMixin {

    @Final
    @Shadow
    private DynamicRegistryManager registryManager;

    /**
     * Injects at the start of TagManagerLoader#reload to determine the number of registries.
     * Sets the expected count in BlacklistTags for correct summary log timing.
     */
    @Inject(
            method = "reload",
            at = @At("HEAD")
    )
    private void item_blacklist$beforeTagReload(
            ResourceReloader.Synchronizer synchronizer,
            ResourceManager manager,
            Profiler prepareProfiler,
            Profiler applyProfiler,
            Executor prepareExecutor,
            Executor applyExecutor,
            CallbackInfoReturnable<CompletableFuture<Void>> cir
    ) {
        int numRegistries = this.registryManager.streamAllRegistries().toList().size();
        BlacklistTags.setExpectedTagRegistryCount(numRegistries);
    }
}
