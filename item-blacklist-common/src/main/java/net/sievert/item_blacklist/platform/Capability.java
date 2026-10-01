package net.sievert.item_blacklist.platform;

import net.sievert.item_blacklist.line.Releases;

/**
 * A feature of the game or a loader that not every supported release has. Each constant
 * records its window, the first release that has it ({@code since}, inclusive) and the first
 * that no longer does ({@code until}, exclusive), and what the mod does outside it. Code
 * outside this package asks {@link Platform#supports(Capability)}, never the release;
 * CLAUDE.md, "Adding a capability".
 */
public enum Capability {
    // Capabilities go here, above the semicolon; CLAUDE.md, "Adding a capability".
    /** Fuel tables are built per server and per connection (FuelValues) from 1.21.2. */
    FUEL_VALUES("1.21.2", null,
            "fuel tables built per server and per connection (FuelValues); blacklisted items"
                    + " leave each as it is built",
            "one static fuel table the port leaves as it is; the furnace's fuel checks refuse"
                    + " blacklisted stacks"),
    /** Potion tags exist from 26.1 (PotionTags, #minecraft:tradeable); 1.21.x has none. */
    POTION_TAGS("26.1", null,
            "potion tags such as #minecraft:tradeable exist, and blacklisted potions leave them",
            "no vanilla potion tag exists; the potion branch of the tag filter has nothing"
                    + " to strip"),
    ;

    private final String since;
    private final String until;
    private final String summary;
    private final String fallback;

    Capability(String since, String until, String summary, String fallback) {
        this.since = since;
        this.until = until;
        this.summary = summary;
        this.fallback = fallback;
    }

    /** First release that has it, e.g. {@code "1.21.6"}; null when every one below until does. */
    public String since() {
        return since;
    }

    /** First release that no longer has it, e.g. {@code "26.2"}; null when none. */
    public String until() {
        return until;
    }

    /** One-line description of what the capability provides. */
    public String summary() {
        return summary;
    }

    /** What the mod does instead on releases outside the window. */
    public String fallback() {
        return fallback;
    }

    /** Whether {@code release} is inside the window: at or above since, below until. */
    public boolean contains(String release) {
        return Releases.inWindow(release, since, until);
    }
}
