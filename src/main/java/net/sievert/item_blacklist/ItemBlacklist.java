package net.sievert.item_blacklist;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static net.sievert.item_blacklist.BlacklistLogger.*;
import static net.sievert.item_blacklist.BlacklistLogger.Group.*;

/**
 * Main mod initializer for Item Blacklist.
 * Handles config loading, validation, and trade filtering setup.
 */
public class ItemBlacklist implements ModInitializer {
	public static final String MOD_ID = "item_blacklist";
	public static BlacklistConfig CONFIG;

	// validation stats
	public static int vanillaValidated = 0;
	public static int moddedValidated = 0;
	public static int totalInvalid = 0;

	@Override
	public void onInitialize() {
		info(INIT, "Initializing Item Blacklist");
		CONFIG = BlacklistConfig.loadOrCreate();

		// Early vanilla-only validation (uses rawBlacklist strings)
		Set<net.minecraft.util.Identifier> vanillaValid = new HashSet<>();
		int vanillaInvalid = BlacklistValidator.validateVanillaOnly(CONFIG.rawBlacklist, vanillaValid);

		vanillaValidated = vanillaValid.size();
		totalInvalid = vanillaInvalid;
		CONFIG.blacklist = new HashSet<>(vanillaValid);

		// Start trade filtering
		BlacklistVillagerTrades.init();

		// Example trades (unrelated to blacklist)
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.FARMER, 1, factories -> {
			factories.add((entity, random) -> new TradeOffer(
					new TradedItem(new ItemStack(Items.EMERALD, 5).getItem()),
					Optional.empty(),
					new ItemStack(Items.TORCH, 1),
					10, 2, 0.05f
			));
		});

		// Flush all logs at once on server started
		ServerLifecycleEvents.SERVER_STARTED.register(server -> flush());
	}
}
