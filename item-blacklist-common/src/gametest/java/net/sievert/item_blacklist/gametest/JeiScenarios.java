package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.integration.jei.JeiHook;
import net.sievert.item_blacklist.integration.jei.JeiRules;
import net.sievert.item_blacklist.network.ClientSyncSlot;
import net.sievert.item_blacklist.platform.Services;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The jei scenarios (prefix jei_). JEI is on no run.py server, only in the dev runs, and no
 * class that names JEI may load on a server without it, so these prove what a server can: the
 * classes JEI and Fabric's entrypoint would load are in the jar, the hook stays inert without
 * a client, and the hiding rules give the expected answer on the stacks JEI builds. They load
 * JeiHook, JeiRules, ClientSyncSlot and Services only, never the bridge, the recipe facade,
 * the plugins or the entry class. Each is synchronous and holds before and after a reload:
 * the live stacks it judges are blacklisted by the fixture's ids, which a reload keeps.
 */
public final class JeiScenarios {
    /** The package of the JEI classes, as a resource path. */
    private static final String PACKAGE = "/net/sievert/item_blacklist/integration/jei/";
    /** In every jar and every dev run of both lines. */
    private static final List<String> EVERYWHERE = List.of("JeiBridge", "JeiHiding", "JeiHook",
            "JeiRules", "line/JeiRecipes", "line/JeiPluginEntry");
    /** The 1.21.x line's classes that the line module itself compiles. */
    private static final List<String> LINE_1_21_FLOOR = List.of("line/JeiRecipesUntil1_21_4",
            "line/ItemBlacklistJeiPluginUntil1_21_11");
    /** The 1.21.x line's classes packed from v1_21_4 and v1_21_11: in the jar, not a dev run. */
    private static final List<String> LINE_1_21_PACKED = List.of("line/JeiRecipesSince1_21_4",
            "line/ItemBlacklistJeiPluginSince1_21_11");
    /** The 26.x line's own plugin class. */
    private static final List<String> LINE_26 = List.of("line/ItemBlacklistJeiPlugin");

    private JeiScenarios() {
    }

    /** The jei_ scenarios; called by ItemBlacklistGameTests.register. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("jei_classes_shipped", JeiScenarios::jeiClassesShipped);
        sink.accept("jei_hook_inert", JeiScenarios::jeiHookInert);
        sink.accept("jei_rules", JeiScenarios::jeiRules);
    }

    /**
     * The classes JEI or Fabric's entrypoint would load are in the mod, and those of one line
     * only; on a production server also the two classes version modules pack into the 1.21.x
     * jar. So JeiPluginEntry's pick cannot fail for a missing class, and NeoForge's scan finds
     * a plugin of the running window. Reads class files as resources, loading none.
     */
    static void jeiClassesShipped(GameTestHelper helper) {
        for (String name : EVERYWHERE) {
            Check.isTrue(helper, shipped(name), "Expected " + name + ".class in the mod");
        }
        boolean line121 = LINE_1_21_FLOOR.stream().allMatch(JeiScenarios::shipped);
        boolean line26 = LINE_26.stream().allMatch(JeiScenarios::shipped);
        Check.isTrue(helper, line121 != line26, "Expected the JEI classes of exactly one line, "
                + "1.21.x " + line121 + ", 26.x " + line26);
        Check.isTrue(helper, LINE_26.stream().noneMatch(JeiScenarios::shipped)
                        || LINE_1_21_FLOOR.stream().noneMatch(JeiScenarios::shipped),
                "Expected no JEI class of the other line");
        // Dev runs load the line module's own classes only; the packed ones come with the jar.
        if (line121 && !Services.PLATFORM.isDevelopmentEnvironment()) {
            for (String name : LINE_1_21_PACKED) {
                Check.isTrue(helper, shipped(name),
                        "Expected " + name + ".class in the 1.21.x jar");
            }
        }
        helper.succeed();
    }

    /**
     * isModLoaded answers both ways; on a physical server no client installed the slot, so both
     * hook calls return at once, twice, without an exception and without resolving a JEI class.
     * A GameTest inside a singleplayer world skips the slot and the hook calls: its client
     * installed the slot, and clearing the bridge there would stop a live JEI's filtering.
     */
    static void jeiHookInert(GameTestHelper helper) {
        Check.isTrue(helper, Services.PLATFORM.isModLoaded(ItemBlacklistMod.MOD_ID),
                "Expected item_blacklist to be loaded");
        Check.isTrue(helper, !Services.PLATFORM.isModLoaded("item_blacklist_no_such_mod"),
                "Expected an unknown mod id to be absent");
        if (!TestGame.server(helper).isSingleplayer()) {
            Check.isTrue(helper, !ClientSyncSlot.installed(),
                    "Expected no client receiver on a server");
            JeiHook.refilter();
            JeiHook.clearRuntime();
            JeiHook.refilter();
        }
        helper.succeed();
    }

    /**
     * The rules the bridge applies to JEI's recipes, on the stacks JEI builds: brewing hides on
     * a blacklisted output or input, or when every ingredient is blacklisted; the anvil hides
     * on any blacklisted stack, stored and tag-derived enchantments included; an empty
     * snapshot hides nothing.
     */
    static void jeiRules(GameTestHelper helper) {
        BlacklistSnapshot live = TestGame.live();
        ItemStack strength = TestGame.potionStack(helper, Items.POTION, "strength");
        ItemStack awkward = TestGame.potionStack(helper, Items.POTION, "awkward");
        ItemStack leaping = TestGame.potionStack(helper, Items.POTION, "leaping");
        ItemStack water = TestGame.potionStack(helper, Items.POTION, "water");
        ItemStack blaze = new ItemStack(Items.BLAZE_POWDER);
        ItemStack rabbit = new ItemStack(Items.RABBIT_FOOT);
        ItemStack sugar = new ItemStack(Items.SUGAR);
        // Brewing, live.
        brewing(helper, live, strength, List.of(awkward), List.of(blaze), true,
                "blacklisted output");
        brewing(helper, live, leaping, List.of(strength), List.of(sugar), true,
                "blacklisted potion input");
        brewing(helper, live, leaping, List.of(awkward), List.of(rabbit), true,
                "every ingredient blacklisted");
        brewing(helper, live, leaping, List.of(awkward), List.of(rabbit, sugar), false,
                "one ingredient allowed");
        brewing(helper, live, leaping, List.of(awkward), List.of(), false, "no ingredient");
        brewing(helper, live, water, List.of(water), List.of(sugar), false, "water control");
        // Anvil, live.
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        anvil(helper, live, List.of(sword), List.of(TestGame.book(helper, "mending")), List.of(),
                true, "book with stored mending");
        anvil(helper, live, List.of(sword), List.of(TestGame.book(helper, "binding_curse")),
                List.of(), true, "tag-derived enchantment");
        anvil(helper, live, List.of(sword), List.of(new ItemStack(Items.OAK_PLANKS)), List.of(),
                true, "blacklisted item");
        anvil(helper, live, List.of(), List.of(),
                List.of(TestGame.enchanted(helper, Items.IRON_SWORD, "mending")), true,
                "enchanted output");
        anvil(helper, live, List.of(sword), List.of(TestGame.book(helper, "unbreaking")),
                List.of(TestGame.enchanted(helper, Items.IRON_SWORD, "unbreaking")), false,
                "unbreaking control");
        anvil(helper, live, List.of(sword), List.of(new ItemStack(Items.IRON_INGOT)),
                List.of(sword), false, "repair control");
        anvil(helper, live, List.of(), List.of(), List.of(), false, "empty recipe");
        // Built snapshots.
        BlacklistSnapshot stoneOnly =
                TestGame.snapshot(b -> b.item(TestGame.key(Registries.ITEM, "stone")));
        anvil(helper, stoneOnly, List.of(new ItemStack(Items.STONE)), List.of(), List.of(), true,
                "built: stone");
        anvil(helper, stoneOnly, List.of(new ItemStack(Items.OAK_PLANKS)), List.of(), List.of(),
                false, "built: planks not in it");
        brewing(helper, BlacklistSnapshot.EMPTY, strength, List.of(awkward), List.of(blaze),
                false, "empty snapshot");
        anvil(helper, BlacklistSnapshot.EMPTY, List.of(sword),
                List.of(TestGame.book(helper, "mending")), List.of(), false, "empty snapshot");
        helper.succeed();
    }

    /** Asserts JeiRules.hideBrewing's answer for one recipe; name says which. */
    private static void brewing(GameTestHelper helper, BlacklistSnapshot snapshot,
            ItemStack output, List<ItemStack> inputs, List<ItemStack> ingredients,
            boolean expected, String name) {
        Check.equal(helper, JeiRules.hideBrewing(snapshot, output, inputs, ingredients),
                expected, "hideBrewing (" + name + ")");
    }

    /** Asserts JeiRules.hideAnvil's answer for one recipe; name says which. */
    private static void anvil(GameTestHelper helper, BlacklistSnapshot snapshot,
            List<ItemStack> left, List<ItemStack> right, List<ItemStack> outputs,
            boolean expected, String name) {
        Check.equal(helper, JeiRules.hideAnvil(snapshot, left, right, outputs), expected,
                "hideAnvil (" + name + ")");
    }

    /** Whether the class file is a resource of the mod, read without loading the class. */
    private static boolean shipped(String name) {
        try (InputStream in = ItemBlacklistMod.class.getResourceAsStream(
                PACKAGE + name + ".class")) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }
}
