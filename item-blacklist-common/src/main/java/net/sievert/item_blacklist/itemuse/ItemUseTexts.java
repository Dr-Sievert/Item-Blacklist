package net.sievert.item_blacklist.itemuse;

/**
 * The player-facing texts of item-use: translation keys with the old mod's English as the
 * fallback. No language file ships, so every client and server shows the fallback unless a
 * resource pack translates the key. JDK only, so the unit tests read it without a game.
 */
public final class ItemUseTexts {
    /** Action-bar line when a held or received stack is deleted; %s is the stack's name. */
    public static final String REMOVED_KEY = "item_blacklist.item_removed";

    /** English of {@link #REMOVED_KEY}: the old mod's text. */
    public static final String REMOVED_FALLBACK = "%s is disabled by blacklist.";

    /** Action-bar line when a click on a blacklisted block is refused; %s is the block's name. */
    public static final String BLOCK_KEY = "item_blacklist.block_refused";

    /** English of {@link #BLOCK_KEY}: the old mod's text. */
    public static final String BLOCK_FALLBACK = "%s is disabled by blacklist.";

    /** The last tooltip line of a blacklisted stack. */
    public static final String TOOLTIP_KEY = "item_blacklist.tooltip";

    /** English of {@link #TOOLTIP_KEY}: the old mod's text. */
    public static final String TOOLTIP_FALLBACK = "Disabled by blacklist.";

    private ItemUseTexts() {
    }
}
