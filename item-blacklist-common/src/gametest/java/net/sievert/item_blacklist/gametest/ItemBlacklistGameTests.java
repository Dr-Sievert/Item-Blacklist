package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.ItemBlacklistMod;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * The scenarios, written once and compiled by every module. Checks with a message go through
 * Check: the message type changed inside 1.21.x.
 *
 * <p>Each GameTest era's adapter turns {@link #register} into tests named NAMESPACE:path: in the
 * annotations era (up to 1.21.4) AnnotationEra builds a vanilla TestFunction for each; from 1.21.5
 * on, the loader's test mod registers them into the vanilla TEST_FUNCTION registry, and the test
 * instances in data/item_blacklist_gametest/test_instance name them. A new scenario is a method
 * in its subsystem's &lt;Name&gt;Scenarios, a line in that class's register and a test instance
 * beside place_stone.json in each of
 * src/since1_21_5/gametest/resources/data/item_blacklist_gametest/test_instance and
 * src/since26_1/gametest/resources/data/item_blacklist_gametest/test_instance.
 */
public final class ItemBlacklistGameTests {
    public static final String NAMESPACE = ItemBlacklistMod.MOD_ID + "_gametest";

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private ItemBlacklistGameTests() {
    }

    /** Every scenario by path; the era's adapter turns each into NAMESPACE:path. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("place_stone", ItemBlacklistGameTests::placeStone);
        CoreScenarios.register(sink);
        TagScenarios.register(sink);
        RecipeScenarios.register(sink);
        LootScenarios.register(sink);
        TradeScenarios.register(sink);
        EnchantmentScenarios.register(sink);
        BrewingScenarios.register(sink);
        ItemUseScenarios.register(sink);
        LoaderFilterScenarios.register(sink);
        NetworkScenarios.register(sink);
        LoggingScenarios.register(sink);
        JeiScenarios.register(sink);
        JerScenarios.register(sink);
        // Last: the other subsystems add their post-reload checks from their own register.
        ReloadScenarios.register(sink);
    }

    /** Place a vanilla block; next tick it is there. */
    static void placeStone(GameTestHelper helper) {
        helper.setBlock(POS, Blocks.STONE);
        helper.startSequence()
                .thenExecuteAfter(1, () -> Check.isTrue(helper,
                        helper.getBlockState(POS).is(Blocks.STONE), "Expected stone at " + POS))
                .thenSucceed();
    }
}
