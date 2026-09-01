package net.sievert.item_blacklist.neoforge;

import net.neoforged.fml.common.Mod;
import net.sievert.item_blacklist.ItemBlacklist;

@Mod(ItemBlacklist.MOD_ID)
public final class ItemBlacklistNeoForge {

    public ItemBlacklistNeoForge() {
        ItemBlacklist.init();
    }
}
