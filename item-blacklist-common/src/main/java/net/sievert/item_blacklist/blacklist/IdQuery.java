package net.sievert.item_blacklist.blacklist;

/**
 * The blacklist as text: all the loot JSON walk and its unit tests see, so that walk names no
 * game type and runs without a game. Ids are "namespace:path", tags without '#'.
 */
public interface IdQuery {
    /** Whether the item is blacklisted, explicitly or as a member of a configured tag. */
    boolean itemId(String id);

    /** Whether the config names this item tag (not a tag the strip only emptied). */
    boolean itemTagId(String id);

    /** Whether the potion is blacklisted. */
    boolean potionId(String id);

    /** Whether the enchantment is blacklisted, explicitly or as a member of a configured tag. */
    boolean enchantmentId(String id);
}
