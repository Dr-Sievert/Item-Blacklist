package net.sievert.item_blacklist.recipes;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.ServerState;

/**
 * What the removal hooks of both windows share: which snapshot a removal pass may judge with.
 * One place, so the 1.21.1 pass and the 1.21.2+ pass run at exactly the same moments.
 */
public final class RecipeHooks {
    private RecipeHooks() {
    }

    /**
     * The running server's snapshot when called on that server's thread, else EMPTY. The world
     * load (1.21.1's apply and updateRegistryTags), a server's constructor (1.21.2+'s first
     * finalizeRecipeLoading) and the state of a server whose stop never came all see EMPTY, so
     * no pass runs there; the forced reload at server start runs the first one.
     */
    public static BlacklistSnapshot passSnapshot() {
        ServerState state = Blacklist.serverState();
        return state != null && state.onServerThread() ? state.snapshot() : BlacklistSnapshot.EMPTY;
    }
}
