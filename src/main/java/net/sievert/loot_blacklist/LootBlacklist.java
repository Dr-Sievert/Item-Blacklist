package net.sievert.loot_blacklist;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LootBlacklist implements ModInitializer {
	public static final String MOD_ID = "loot_blacklist";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static LootBlacklistConfig CONFIG;

	private static boolean validated = false;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {}", MOD_ID);
		CONFIG = LootBlacklistConfig.loadOrCreate();

		// No longer needed for core logic; keep only for other mods/datapacks or diagnostics.
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			ensureValidated();
			// (Optional: leave empty, or just print diagnostics)
		});

		LootTableEvents.ALL_LOADED.register((manager, registry) -> {
			LOGGER.info("[{}] Summary: loaded blacklist with {} entries", MOD_ID, CONFIG.blacklist.size());
		});
	}

	public static void ensureValidated() {
		if (!validated) {
			CONFIG.validateEntries();
			validated = true;
			LOGGER.info("[{}] Blacklist validated with {} entries", MOD_ID, CONFIG.blacklist.size());
		}
	}

	// (Optional) Runtime helper
	public static boolean isBlacklisted(String id) {
		ensureValidated();
		return CONFIG.blacklist.contains(id);
	}
}
