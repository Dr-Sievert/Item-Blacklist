package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.blacklist.ServerState;
import net.sievert.item_blacklist.line.Keys;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The reload scenario (prefix reload_): a datapack reload inside a running server keeps every
 * filter. Other subsystems add their own checks after the reload through afterReload, from
 * their own register, so none of them edits this class; register runs last, after theirs.
 */
public final class ReloadScenarios {
    /** The checks of the other subsystems, by owner, run in owner order after the reload. */
    private static final Map<String, Consumer<GameTestHelper>> CHECKS =
            new ConcurrentSkipListMap<>();

    private ReloadScenarios() {
    }

    /** A subsystem's assertion after the reload, added from its register; one per owner. */
    public static void afterReload(String owner, Consumer<GameTestHelper> check) {
        CHECKS.put(owner, check);
    }

    /** Called by ItemBlacklistGameTests.register, last. */
    public static void register(BiConsumer<String, Consumer<GameTestHelper>> sink) {
        sink.accept("reload_keeps_filters", ReloadScenarios::reloadKeepsFilters);
    }

    /**
     * A full reload with a survival player in the list, as /reload runs it: on the server
     * thread the call blocks until the reload is done, so the scenario stays synchronous. The
     * reload is counted and reported once more, and the tags are filtered again: birch planks
     * stay blacklisted through #planks while #planks is bound empty.
     */
    static void reloadKeepsFilters(GameTestHelper helper) {
        MinecraftServer server = TestGame.server(helper);
        ServerState state = Blacklist.serverState();
        if (state == null) {
            Check.fail(helper, "No server state: Lifecycle.starting did not run");
            return;
        }
        int reloads = state.reloads();
        int flushes = state.report().lastFlush().number();
        TestPlayers.withSurvival(helper, player -> {
            try {
                server.reloadResources(server.getPackRepository().getSelectedIds()).join();
            } catch (CompletionException e) {
                Check.fail(helper, "The reload failed: " + e.getCause());
            }
        });
        Check.equal(helper, state.reloads(), reloads + 1, "the reload count after a reload");
        Check.equal(helper, state.report().lastFlush().number(), flushes + 1,
                "the report's flush number after a reload");
        Check.isTrue(helper, Blacklist.serverState() == state,
                "The reload keeps the server's state");
        Check.isTrue(helper, TestGame.live().item(Items.BIRCH_PLANKS),
                "birch_planks stays blacklisted through #minecraft:planks after a reload");
        TagKey<Item> planks = Keys.tag(Registries.ITEM, "minecraft", "planks");
        int bound = Lookups.members(server.registryAccess(), Registries.ITEM, planks).size();
        Check.equal(helper, bound, 0, "the size of #minecraft:planks after a reload");
        for (Consumer<GameTestHelper> check : CHECKS.values()) {
            check.accept(helper);
        }
        helper.succeed();
    }
}
