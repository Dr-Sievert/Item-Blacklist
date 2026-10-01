package net.sievert.item_blacklist.log;

/**
 * The subject of a log line, printed as "[TAG]" after the mod's name. The nine tags of the
 * old mod's log, so a reader of both logs finds the same words.
 */
public enum LogTag {
    /** The config file: its load, its warnings, unknown entries. */
    CONFIG,
    /** Enchantments and enchantment tags. */
    ENCHANTMENT,
    /** Start-up: resolving, the start reload, the report's header. */
    INIT,
    /** Items, and the loader tables items leave. */
    ITEM,
    /** Loot tables. */
    LOOT,
    /** Potions, and NeoForge's brewing registry when it cannot be reached. */
    POTION,
    /** Recipes, brewing recipes included, and recipe viewers. */
    RECIPE,
    /** Item and block tags. */
    TAG,
    /** Trade offers. */
    TRADE
}
