package net.sievert.item_blacklist.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.sievert.item_blacklist.ItemBlacklist;
import org.jetbrains.annotations.NotNull;

@JeiPlugin
public final class ItemBlacklistJeiPlugin
        implements IModPlugin {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(
                    ItemBlacklist.MOD_ID,
                    "jei"
            );

    @Override
    public @NotNull ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void onRuntimeAvailable(
            @NotNull IJeiRuntime jeiRuntime
    ) {
        BlacklistJeiManager.setRuntime(
                jeiRuntime
        );
    }

    @Override
    public void onRuntimeUnavailable() {
        BlacklistJeiManager.clearRuntime();
    }
}