package net.sievert.loot_blacklist;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableSource;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for the Loot Blacklist mod.
 */
public class LootBlacklist implements ModInitializer {

	/** Mod ID constant */
	public static final String MOD_ID = "loot_blacklist";

	/** Logger instance for this mod */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Global config, loaded at startup */
	public static LootBlacklistConfig CONFIG;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Loot Blacklist");
		CONFIG = LootBlacklistConfig.loadOrCreate();

		LootTableEvents.MODIFY.register((RegistryKey<LootTable> key,
										 LootTable.Builder tableBuilder,
										 LootTableSource source,
										 RegistryWrapper.WrapperLookup registries) -> {
			if (!CONFIG.blacklist.isEmpty()) {
				Identifier tableId = key.getValue();
				int removed = LootFilter.filterLootTableBuilder(tableBuilder, CONFIG.blacklist, tableId);

				if (removed > 0) {
					LOGGER.info("Applied loot blacklist: removed {} entries from {}", removed, tableId);
				}
			}
		});
	}
}
