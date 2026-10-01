package net.sievert.item_blacklist.neoforge.gametest;

import net.sievert.item_blacklist.gametest.Fixtures;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;

/**
 * NeoForge's own brewing recipes for brew_loader_recipe_removed, which only NeoForge can set
 * up: stone + stick gives a lingering potion (removed by its output), stone + rabbit foot iron
 * (removed by its reagent), stone + sugar coal (kept). Stone is no vanilla brewing input, so no
 * vanilla mix answers for these pairs.
 */
final class NeoForgeBrewingFixture {
    private NeoForgeBrewingFixture() {
    }

    /**
     * Called by the test mod's constructor: the server's PotionBrewing is built later, in the
     * server's constructor, which posts the event. The flag is set here, not in the listener,
     * so a listener that never fires fails the scenario instead of passing it on its control.
     */
    static void register() {
        NeoForge.EVENT_BUS.addListener(RegisterBrewingRecipesEvent.class,
                NeoForgeBrewingFixture::add);
        Fixtures.loaderBrewingRecipe = true;
    }

    private static void add(RegisterBrewingRecipesEvent event) {
        PotionBrewing.Builder builder = event.getBuilder();
        builder.addRecipe(ingredient(Items.STONE), ingredient(Items.STICK),
                new ItemStack(Items.LINGERING_POTION));
        builder.addRecipe(ingredient(Items.STONE), ingredient(Items.RABBIT_FOOT),
                new ItemStack(Items.IRON_INGOT));
        builder.addRecipe(ingredient(Items.STONE), ingredient(Items.SUGAR),
                new ItemStack(Items.COAL));
    }

    /** Ingredient.of(ItemLike...) on every release: the one-argument form exists from 1.21.2. */
    private static Ingredient ingredient(ItemLike... items) {
        return Ingredient.of(items);
    }
}
