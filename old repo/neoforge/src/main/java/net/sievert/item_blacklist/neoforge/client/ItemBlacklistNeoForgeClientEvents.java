package net.sievert.item_blacklist.neoforge.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.blacklist.BlacklistManager;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(
        modid = ItemBlacklist.MOD_ID,
        value = Dist.CLIENT
)
public final class ItemBlacklistNeoForgeClientEvents {

    private ItemBlacklistNeoForgeClientEvents() {}

    @SubscribeEvent
    public static void onLoggingIn(
            ClientPlayerNetworkEvent.LoggingIn event
    ) {
        Minecraft minecraft =
                Minecraft.getInstance();

        BlacklistManager.setRemoteClient(
                !minecraft.hasSingleplayerServer()
        );
    }

    @SubscribeEvent
    public static void onLoggingOut(
            ClientPlayerNetworkEvent.LoggingOut event
    ) {
        BlacklistManager.setRemoteClient(false);
    }
}