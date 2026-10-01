package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.line.RecipeIds;
import net.sievert.item_blacklist.report.ReportSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * The recipes scenarios (prefix recipes_): what the removal pass left in the running server's
 * recipe manager and recorded in its report, and that a blacklisted item matches no ingredient
 * and is never counted by the recipe picker. Each is synchronous: set up, act, assert and
 * succeed inside its call. Each holds before and after a reload, since every reload runs the
 * pass again and its report's flush records it again.
 */
public final class RecipeScenarios {
    private RecipeScenarios() {
    }

    /**
     * The recipes_ scenarios, and the recipes check of reload_keeps_filters; called by
     * ItemBlacklistGameTests.register, before ReloadScenarios.register.
     */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("recipes_facades_link", RecipeScenarios::facadesLink);
        sink.accept("recipes_ingredient_refuses", RecipeScenarios::ingredientRefuses);
        sink.accept("recipes_picker_refuses", RecipeScenarios::pickerRefuses);
        sink.accept("recipes_removed_by_ingredient", RecipeScenarios::removedByIngredient);
        sink.accept("recipes_removed_by_result", RecipeScenarios::removedByResult);
        sink.accept("recipes_removed_by_tag", RecipeScenarios::removedByTag);
        sink.accept("recipes_shaped_grid_refuses", RecipeScenarios::shapedGridRefuses);
        // Both passes run inside every reload, so the removals by tag and by an emptied tag
        // hold after reload_keeps_filters' reload too.
        ReloadScenarios.afterReload("recipes", helper -> {
            Set<String> names = names(helper);
            absent(helper, names, "minecraft:stick");
            absent(helper, names, "minecraft:soul_torch");
        });
    }

    /**
     * A recipe that names a configured tag goes, and the report names the tag as its cause;
     * the other stick recipe, from bamboo, stays.
     */
    static void removedByTag(GameTestHelper helper) {
        Set<String> names = names(helper);
        absent(helper, names, "minecraft:stick");
        present(helper, names, "minecraft:stick_from_bamboo_item");
        recorded(helper, "minecraft:stick", "#minecraft:planks");
        helper.succeed();
    }

    /**
     * A recipe whose result is blacklisted goes: through a tag (acacia planks, a member of
     * #planks) and by name (charcoal, a smelting recipe). A recipe with a blacklisted
     * alternative beside an allowed one stays (the torch: coal or charcoal).
     */
    static void removedByResult(GameTestHelper helper) {
        Set<String> names = names(helper);
        absent(helper, names, "minecraft:acacia_planks");
        absent(helper, names, "minecraft:charcoal");
        present(helper, names, "minecraft:torch");
        recorded(helper, "minecraft:acacia_planks", "minecraft:acacia_planks");
        recorded(helper, "minecraft:charcoal", "minecraft:charcoal");
        helper.succeed();
    }

    /**
     * A recipe goes when an ingredient's alternatives are all blacklisted: a sole blacklisted
     * item (the oak button), a tag the strip emptied without the config naming it (soul sand
     * and soul soil empty #soul_fire_base_blocks). A tag only partly stripped keeps its recipe
     * (#coals in the campfire).
     */
    static void removedByIngredient(GameTestHelper helper) {
        Set<String> names = names(helper);
        absent(helper, names, "minecraft:oak_button");
        absent(helper, names, "minecraft:soul_torch");
        absent(helper, names, "minecraft:soul_campfire");
        present(helper, names, "minecraft:campfire");
        recorded(helper, "minecraft:oak_button", "minecraft:oak_planks");
        recorded(helper, "minecraft:soul_torch", "#minecraft:soul_fire_base_blocks");
        recorded(helper, "minecraft:soul_campfire", "#minecraft:soul_fire_base_blocks");
        helper.succeed();
    }

    /** Ingredient.test refuses a blacklisted item and matches its allowed alternative. */
    static void ingredientRefuses(GameTestHelper helper) {
        Ingredient coals = Ingredient.of(Items.COAL, Items.CHARCOAL);
        Check.isTrue(helper, !coals.test(new ItemStack(Items.CHARCOAL)),
                "Expected charcoal to match no ingredient");
        Check.isTrue(helper, coals.test(new ItemStack(Items.COAL)),
                "Expected coal to match coal or charcoal");
        helper.succeed();
    }

    /**
     * A shaped recipe that stays (the torch) is matched through Ingredient.test, so its
     * blacklisted alternative is inert: charcoal over a stick crafts nothing, coal a torch.
     */
    static void shapedGridRefuses(GameTestHelper helper) {
        Check.equal(helper,
                crafted(helper, 1, 2, new ItemStack(Items.CHARCOAL), new ItemStack(Items.STICK)),
                Optional.<String>empty(), "the recipe of charcoal over a stick");
        Check.equal(helper,
                crafted(helper, 1, 2, new ItemStack(Items.COAL), new ItemStack(Items.STICK)),
                Optional.of("minecraft:torch"), "the recipe of coal over a stick");
        helper.succeed();
    }

    /**
     * A shapeless recipe of several slots is matched through the stacked contents, not through
     * Ingredient.test: the picker hook keeps charcoal out of them, so the fire charge's third
     * slot takes coal only.
     */
    static void pickerRefuses(GameTestHelper helper) {
        Check.equal(helper, crafted(helper, 3, 1, new ItemStack(Items.GUNPOWDER),
                        new ItemStack(Items.BLAZE_POWDER), new ItemStack(Items.CHARCOAL)),
                Optional.<String>empty(), "the recipe of gunpowder, blaze powder, charcoal");
        Check.equal(helper, crafted(helper, 3, 1, new ItemStack(Items.GUNPOWDER),
                        new ItemStack(Items.BLAZE_POWDER), new ItemStack(Items.COAL)),
                Optional.of("minecraft:fire_charge"),
                "the recipe of gunpowder, blaze powder, coal");
        helper.succeed();
    }

    /**
     * Both members of RecipeIds run, so every Fabric 1.21.x boot executes the backend of its
     * window (RecipeIdsUntil1_21_2 on 1.21.1, RecipeIdsSince1_21_2 from 1.21.2).
     */
    static void facadesLink(GameTestHelper helper) {
        RecipeManager manager = TestGame.server(helper).getRecipeManager();
        Set<String> names = RecipeIds.names(manager);
        Check.equal(helper, names.size(), manager.getRecipes().size(), "the number of recipe ids");
        boolean named = false;
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            named |= "minecraft:torch".equals(RecipeIds.name(holder));
        }
        Check.isTrue(helper, named && names.contains("minecraft:torch"),
                "Expected RecipeIds to name minecraft:torch");
        helper.succeed();
    }

    /** Every recipe id of the running server; fails when the manager looks unloaded. */
    private static Set<String> names(GameTestHelper helper) {
        Set<String> names = RecipeIds.names(TestGame.server(helper).getRecipeManager());
        Check.isTrue(helper, names.size() > 100,
                "Expected the vanilla recipes to be loaded, found " + names.size());
        return names;
    }

    private static void absent(GameTestHelper helper, Set<String> names, String recipe) {
        Check.isTrue(helper, !names.contains(recipe),
                "Expected recipe " + recipe + " to be removed");
    }

    private static void present(GameTestHelper helper, Set<String> names, String recipe) {
        Check.isTrue(helper, names.contains(recipe), "Expected recipe " + recipe + " to stay");
    }

    /** The last flushed report holds (recipe, cause) under RECIPE. */
    private static void recorded(GameTestHelper helper, String recipe, String cause) {
        ServerState state = Blacklist.serverState();
        if (state == null) {
            Check.fail(helper, "No server state: Lifecycle.starting did not run");
            return;
        }
        SortedMap<String, SortedSet<String>> recipes =
                state.report().lastFlush().entries().get(ReportSnapshot.Kind.RECIPE);
        SortedSet<String> causes = recipes == null ? null : recipes.get(recipe);
        Check.isTrue(helper, causes != null && causes.contains(cause),
                "Expected the last report to record " + recipe + " removed for " + cause
                        + ", was " + causes);
    }

    /** The id of the crafting recipe the grid, filled row by row, matches, if any. */
    private static Optional<String> crafted(GameTestHelper helper, int width, int height,
            ItemStack... stacks) {
        CraftingInput input = CraftingInput.of(width, height, List.of(stacks));
        return TestGame.server(helper).getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(RecipeIds::name);
    }
}
