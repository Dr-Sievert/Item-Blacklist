package net.sievert.item_blacklist.gametest;

/**
 * What only a loader can set up for a scenario: each loader's test mod registers its fixture
 * and sets the flag, and the common scenario asserts through vanilla calls, passing on its
 * control where the flag is false. Also the path of the config fixture, spelt once.
 */
public final class Fixtures {
    /** The config fixture in the test jar, the config of every GameTest server. */
    public static final String CONFIG_RESOURCE = "item_blacklist_gametest/fixture.jsonc";

    /** A loot modifier (NeoForge) or a MODIFY_DROPS listener (Fabric from 1.21.6) is installed. */
    public static volatile boolean lootAdditions;

    /** NeoForge's RegisterBrewingRecipesEvent fixture is installed. */
    public static volatile boolean loaderBrewingRecipe;

    /**
     * The loader judges a fuel stack, not only its item, on every release: NeoForge's
     * burn-time listener (NeoForgeLoaderFilters). Set by that loader's test mod; on Fabric only
     * the 1.21.1 query hook judges stacks, which Capability.FUEL_VALUES tells apart.
     */
    public static volatile boolean loaderFuelStackHook;

    private Fixtures() {
    }
}
