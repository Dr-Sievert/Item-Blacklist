package net.sievert.loot_blacklist;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class LootBlacklist implements ModInitializer {
	public static final String MOD_ID = "loot_blacklist";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static LootBlacklistConfig CONFIG;

	// validation stats
	public static int vanillaValidated = 0;
	public static int moddedValidated = 0;
	public static int totalInvalid = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing {}", MOD_ID);
		CONFIG = LootBlacklistConfig.loadOrCreate();

		// Early vanilla-only validation (uses rawBlacklist strings)
		Set<net.minecraft.util.Identifier> vanillaValid = new HashSet<>();
		int vanillaInvalid = BlacklistValidator.validateVanillaOnly(CONFIG.rawBlacklist, LOGGER, vanillaValid);

		vanillaValidated = vanillaValid.size();
		totalInvalid = vanillaInvalid; // start totalInvalid with vanilla-invalid count
		CONFIG.blacklist = new HashSet<>(vanillaValid); // working validated set (vanilla only for now)

		// start trade filtering, etc.
		VillagerTradeBlacklist.init();

		// Example trades (unrelated to blacklist)
		TradeOfferHelper.registerVillagerOffers(VillagerProfession.FARMER, 1, factories -> {
			factories.add((entity, random) -> new TradeOffer(
					new TradedItem(new ItemStack(Items.EMERALD, 5).getItem()),
					Optional.empty(),
					new ItemStack(Items.TORCH, 1),
					10, 2, 0.05f
			));
		});

		TradeOfferHelper.registerVillagerOffers(VillagerProfession.TOOLSMITH, 1, factories -> {
			factories.add((entity, random) -> new TradeOffer(
					new TradedItem(new ItemStack(Items.EMERALD, 5).getItem()),
					Optional.empty(),
					new ItemStack(Items.TORCH, 1),
					10, 2, 0.05f
			));
		});
	}
}
