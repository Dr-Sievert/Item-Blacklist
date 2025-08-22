package net.sievert.loot_blacklist;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LootBlacklist implements ModInitializer {
	public static final String MOD_ID = "loot_blacklist";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static LootBlacklistConfig CONFIG;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {}", MOD_ID);
		CONFIG = LootBlacklistConfig.loadOrCreate();
		VillagerTradeBlacklist.init();
	}
}
