package net.sievert.item_blacklist.brewing;

import java.util.List;

/**
 * The text of a removed brewing mix or recipe, the report's subject: the old log's form
 * ("input=minecraft:awkward, ingredient=[minecraft:blaze_powder], output=minecraft:strength"),
 * so a reader of both logs finds the same words. JDK only, so the unit tests reach it without
 * a game.
 */
public final class BrewingText {
    /**
     * Stands for a holder or an item without a registry key. It holds no ':', so it can never
     * equal an id.
     */
    public static final String DIRECT = "[direct]";

    private BrewingText() {
    }

    /** "input=<input>, ingredient=[a, b], output=<output>". */
    public static String mix(String input, List<String> ingredient, String output) {
        return "input=" + input + ", ingredient=" + ingredient + ", output=" + output;
    }
}
