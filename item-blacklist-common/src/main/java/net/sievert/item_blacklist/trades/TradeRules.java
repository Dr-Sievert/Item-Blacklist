package net.sievert.item_blacklist.trades;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Components;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.sievert.item_blacklist.line.Keys;
import net.sievert.item_blacklist.report.Recorder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The trade rules, pure functions of a snapshot handed in: no trade table is read or written,
 * a finished offer is judged where the game makes it, and a refused one becomes null, which
 * every caller skips. The offer hooks and the two 1.21.x guards call these; they never ask
 * Blacklist themselves, so scenarios test them on built snapshots.
 */
public final class TradeRules {
    /** The cause recorded for a tipped arrow that carries no potion. */
    public static final String NO_POTION = "tipped arrow without a potion";
    /** The name used where a registry gives no key, and for a merchant that is not known. */
    public static final String UNKNOWN = "unknown";

    private TradeRules() {
    }

    /**
     * False when cost A, cost B or the result is blacklisted by the stack rule, or when the
     * snapshot holds potions and the result is a tipped arrow without a potion (26.x hands such
     * an arrow out when every potion of its pool is blacklisted).
     */
    public static boolean offerAllowed(BlacklistSnapshot snapshot, MerchantOffer offer) {
        return refusal(snapshot, offer) == null;
    }

    /**
     * The id that refuses the offer ("namespace:path", or {@link #NO_POTION}), or null when the
     * offer is allowed. Cost A is the base stack the merchant asks for, not the one its price
     * modifiers scaled; an offer without cost B has an empty one, which is never refused.
     */
    public static String refusal(BlacklistSnapshot snapshot, MerchantOffer offer) {
        // The cheap check first: hooks may run on a client (JER), with an empty snapshot there.
        if (snapshot.isEmpty()) {
            return null;
        }
        String cause = stackCause(snapshot, offer.getBaseCostA());
        if (cause == null) {
            cause = stackCause(snapshot, offer.getCostB());
        }
        if (cause == null) {
            cause = stackCause(snapshot, offer.getResult());
        }
        if (cause == null && unpotionedArrow(snapshot, offer.getResult())) {
            cause = NO_POTION;
        }
        return cause;
    }

    /**
     * The offer when it is allowed; null when the offer is null or refused. A refusal is
     * recorded as tradeRefusal(merchant's name, cause), so the next flush counts it.
     */
    public static MerchantOffer admit(BlacklistSnapshot snapshot, MerchantOffer offer,
            Entity merchant, Recorder recorder) {
        if (offer == null) {
            return null;
        }
        String cause = refusal(snapshot, offer);
        if (cause == null) {
            return offer;
        }
        recorder.tradeRefusal(merchantName(merchant), cause);
        return null;
    }

    /**
     * A 1.21.x enchanted-book listing: false when its tag is named in the config or was left
     * empty by the last strip, where vanilla would sell a plain book instead of giving no offer.
     * An empty blacklist never refuses one.
     */
    public static boolean bookListingAllowed(BlacklistSnapshot snapshot,
            TagKey<Enchantment> tag) {
        return !snapshot.enchantmentTag(tag) && !snapshot.enchantmentTagEmptied(tag);
    }

    /**
     * A 1.21.x tipped-arrow listing: true when no potion has effects, is brewable by this
     * brewing and is not blacklisted. That is vanilla's pool plus "not blacklisted"; vanilla
     * throws when it picks from an empty pool, so the listing must then give no offer.
     */
    public static boolean arrowPoolEmpty(BlacklistSnapshot snapshot, PotionBrewing brewing) {
        for (Potion potion : BuiltInRegistries.POTION) {
            Holder<Potion> holder = BuiltInRegistries.POTION.wrapAsHolder(potion);
            if (!potion.getEffects().isEmpty() && brewing.isBrewablePotion(holder)
                    && !snapshot.potion(holder)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The merchant's entity type as "namespace:path" ("minecraft:villager"), the report's
     * subject of a refusal; {@link #UNKNOWN} for null. The villager's profession is not used:
     * its classes move and its getter changes inside the 1.21.x line.
     */
    public static String merchantName(Entity merchant) {
        if (merchant == null) {
            return UNKNOWN;
        }
        return BuiltInRegistries.ENTITY_TYPE.getResourceKey(merchant.getType())
                .map(key -> Keys.name(key)).orElse(UNKNOWN);
    }

    /** The id the stack rule refuses a stack for, or null when the stack is allowed. */
    static String stackCause(BlacklistSnapshot snapshot, ItemStack stack) {
        return switch (StackRules.reason(snapshot, stack)) {
            case ITEM -> Lookups.itemName(stack.getItem(), UNKNOWN);
            case ENCHANTMENT -> {
                String cause = enchantmentCause(snapshot,
                        Components.get(stack, DataComponents.ENCHANTMENTS));
                if (cause == null) {
                    cause = enchantmentCause(snapshot,
                            Components.get(stack, DataComponents.STORED_ENCHANTMENTS));
                }
                yield cause == null ? UNKNOWN : cause;
            }
            case POTION -> {
                PotionContents contents = Components.get(stack, DataComponents.POTION_CONTENTS);
                yield contents == null ? UNKNOWN : contents.potion()
                        .flatMap(Holder::unwrapKey).map(key -> Keys.name(key)).orElse(UNKNOWN);
            }
            case NONE -> null;
        };
    }

    /** The first blacklisted enchantment's id, or null; null for a stack without the component. */
    private static String enchantmentCause(BlacklistSnapshot snapshot,
            ItemEnchantments enchantments) {
        if (enchantments == null) {
            return null;
        }
        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (snapshot.enchantment(holder)) {
                return holder.unwrapKey().map(key -> Keys.name(key)).orElse(UNKNOWN);
            }
        }
        return null;
    }

    /**
     * A tipped arrow without a potion, refused only while the blacklist holds potions: then a
     * plain arrow means the pool had nothing left. Custom effects are not looked at. The item's
     * default component is PotionContents.EMPTY, so a new arrow falls under this too.
     */
    private static boolean unpotionedArrow(BlacklistSnapshot snapshot, ItemStack stack) {
        if (snapshot.potions().isEmpty() || stack.getItem() != Items.TIPPED_ARROW) {
            return false;
        }
        PotionContents contents = Components.get(stack, DataComponents.POTION_CONTENTS);
        return contents == null || contents.potion().isEmpty();
    }
}
