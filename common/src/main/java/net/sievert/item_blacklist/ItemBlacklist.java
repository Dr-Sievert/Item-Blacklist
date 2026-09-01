package net.sievert.item_blacklist;

import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.util.ItemBlacklistLogs;

import static net.sievert.item_blacklist.util.ItemBlacklistLogTags.INIT;

public final class ItemBlacklist {

    public static final String MOD_ID = "item_blacklist";

    public static BlacklistConfig CONFIG;

    private ItemBlacklist() {}

    public static void init() {
        System.out.println(
                "fabric.log.level = " + System.getProperty("fabric.log.level")
        );

        ItemBlacklistLogs.info(
                INIT,
                "Initializing Item Blacklist"
        );

        ItemBlacklistLogs.debug(
                INIT,
                "Initializing Item Blacklist"
        );

        CONFIG = BlacklistConfig.loadOrCreate();
    }
}