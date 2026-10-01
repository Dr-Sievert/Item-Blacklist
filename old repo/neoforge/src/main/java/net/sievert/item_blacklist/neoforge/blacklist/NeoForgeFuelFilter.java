package net.sievert.item_blacklist.neoforge.blacklist;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.furnace.FurnaceFuelBurnTimeEvent;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.blacklist.BlacklistManager;

@SuppressWarnings("removal")
@EventBusSubscriber(
        modid = ItemBlacklist.MOD_ID,
        bus = EventBusSubscriber.Bus.GAME
)
public final class NeoForgeFuelFilter {

    private NeoForgeFuelFilter() {}

    @SubscribeEvent
    public static void onFuelBurnTime(
            FurnaceFuelBurnTimeEvent event
    ) {
        if (!BlacklistManager.isBlacklisted(
                event.getItemStack()
        )) {
            return;
        }

        event.setBurnTime(
                0
        );
    }
}