package net.sievert.loot_blacklist;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.entry.EmptyEntry;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.sievert.loot_blacklist.mixin.ItemEntryAccessor;
import net.sievert.loot_blacklist.mixin.LootTableBuilderAccessor;
import net.sievert.loot_blacklist.mixin.LootPoolAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class LootBlacklist implements ModInitializer {
	public static final String MOD_ID = "loot_blacklist";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static LootBlacklistConfig CONFIG;

	private static boolean validated = false;
	private static int totalModifyCalls = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {}", MOD_ID);
		CONFIG = LootBlacklistConfig.loadOrCreate();

		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			ensureValidated();

			// Access and snapshot current pools via accessor
			List<LootPool> originalPools = new ArrayList<>(((LootTableBuilderAccessor) tableBuilder).getPools().build());
			List<LootPool> rebuiltPools = new ArrayList<>();

			for (LootPool pool : originalPools) {
				LootPool.Builder rebuilt = LootPool.builder()
						.rolls(pool.rolls)
						.bonusRolls(pool.bonusRolls);

				pool.conditions.forEach(rebuilt::conditionally);
				pool.functions.forEach(rebuilt::apply);

				// Access all entries via accessor
				for (LootPoolEntry entry : ((LootPoolAccessor) pool).getEntries()) {
					Identifier id = getEntryId(entry);
					if (id != null && CONFIG.blacklist.contains(id)) {
						rebuilt.with(EmptyEntry.builder());
						totalModifyCalls++;
						LOGGER.info("[{}] {}: blacklisted, replaced {} with empty", MOD_ID, key.getValue(), id);
					} else {
						rebuilt.with(entry);
					}
				}

				rebuiltPools.add(rebuilt.build());
			}

			ImmutableList.Builder<LootPool> newBuilder = ImmutableList.builder();
			rebuiltPools.forEach(newBuilder::add);
			((LootTableBuilderAccessor) tableBuilder).setPools(newBuilder);
		});


		LootTableEvents.ALL_LOADED.register((manager, registry) -> {
			LOGGER.info("[{}] Summary: MODIFY={}", MOD_ID, totalModifyCalls);
		});
	}

	private static void ensureValidated() {
		if (!validated) {
			CONFIG.validateEntries();
			validated = true;
			LOGGER.info("[{}] Blacklist validated with {} entries", MOD_ID, CONFIG.blacklist.size());
		}
	}

	private static Identifier getEntryId(LootPoolEntry entry) {
		if (entry instanceof ItemEntry itemEntry) {
			RegistryEntry<Item> regEntry = ((ItemEntryAccessor) itemEntry).getItemEntry();
			if (regEntry != null) {
				return Registries.ITEM.getId(regEntry.value());
			}
		}
		return null;
	}
}
