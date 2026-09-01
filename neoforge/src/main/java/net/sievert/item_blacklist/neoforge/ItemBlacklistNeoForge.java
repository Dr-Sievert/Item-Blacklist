package net.sievert.item_blacklist.neoforge;

import net.neoforged.fml.common.Mod;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.neoforge.platform.NeoForgeEnvironmentService;
import net.sievert.item_blacklist.platform.ItemBlacklistServices;

@Mod(ItemBlacklist.MOD_ID)
public final class ItemBlacklistNeoForge {

    public ItemBlacklistNeoForge() {
        ItemBlacklistServices.registerEnvironment(
                new NeoForgeEnvironmentService()
        );

        ItemBlacklist.init();
    }
}