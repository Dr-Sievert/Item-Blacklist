package net.sievert.item_blacklist.gametest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.TestFunction;

/**
 * The annotations era (up to 1.21.4): each scenario of {@link ItemBlacklistGameTests} becomes a
 * vanilla TestFunction, built in code by a {@link GameTestGenerator} and registered into
 * vanilla's GameTestRegistry, which 1.21.5 removed. The loader's GameTest server runs that
 * registry (README.md, "Testing across versions", says how run.py starts it). Names,
 * structure and limits match the test instances of the later eras
 * (data/item_blacklist_gametest/test_instance). Each loader's test mod calls {@link #register}.
 */
public final class AnnotationEra {
    /** One batch for all scenarios. */
    public static final String BATCH = ItemBlacklistGameTests.NAMESPACE;
    /** The shared empty structure, data/item_blacklist_gametest/structure/empty.nbt. */
    public static final String STRUCTURE = ItemBlacklistGameTests.NAMESPACE + ":empty";
    public static final int MAX_TICKS = 20;

    /** GameTestRegistry creates an instance to call the generator, so the constructor is public. */
    public AnnotationEra() {
    }

    @GameTestGenerator
    public Collection<TestFunction> tests() {
        List<TestFunction> tests = new ArrayList<>();
        ItemBlacklistGameTests.register((path, function) -> tests.add(new TestFunction(
                BATCH, ItemBlacklistGameTests.NAMESPACE + ":" + path,
                STRUCTURE, MAX_TICKS, 0L, true, function)));
        return tests;
    }

    /** Registers the scenarios into vanilla's GameTestRegistry. */
    public static void register() {
        GameTestRegistry.register(AnnotationEra.class);
    }
}
