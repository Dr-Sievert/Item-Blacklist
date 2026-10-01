package net.sievert.item_blacklist.integration.jei;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.integration.jei.line.JeiRecipes;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import java.lang.ref.WeakReference;
import java.util.Set;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;
import mezz.jei.api.recipe.vanilla.IJeiBrewingRecipe;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;

/**
 * Every use of JEI's runtime: hides the brewing and anvil recipes the synced blacklist
 * forbids, and shows again what it hid when the blacklist shrinks. Nothing loads this class
 * unless JEI is loaded on a client ({@link JeiHook}) or JEI calls a plugin class. The types it
 * names (IJeiRuntime, IJeiBrewingRecipe, IJeiAnvilRecipe and their getters) are the same in
 * JEI 19 to 30, so the binary check compares it with every module's JEI pin; the calls whose
 * types changed go through {@link JeiRecipes}. JEI expects the client thread, so every entry
 * hops there, and every entry catches what a changed JEI build throws: JEI's filtering is
 * lost, the game is not.
 */
public final class JeiBridge {
    /** The active runtime, null between onRuntimeUnavailable and the next start. Client thread. */
    private static IJeiRuntime runtime;
    /** The runtime the two hidden sets belong to; weak, so a discarded runtime is not kept. */
    private static WeakReference<IJeiRuntime> owner = new WeakReference<>(null);
    /** The brewing recipes this mod hid on owner, by identity. */
    private static Set<IJeiBrewingRecipe> hiddenBrewing = JeiHiding.identitySet();
    /** The anvil recipes this mod hid on owner, by identity. */
    private static Set<IJeiAnvilRecipe> hiddenAnvil = JeiHiding.identitySet();
    /** generation() of the snapshot of the last complete pass on owner; -1 forces the next. */
    private static long passGeneration = -1L;

    private JeiBridge() {
    }

    /**
     * JEI started (IModPlugin.onRuntimeAvailable): keeps the runtime and always runs a full
     * pass, since JEI rebuilt its lists. The same runtime again keeps what this mod hid on it.
     */
    public static void setRuntime(IJeiRuntime jeiRuntime) {
        onClientThread("runtime start", () -> {
            runtime = jeiRuntime;
            if (owner.get() != jeiRuntime) {
                // A new runtime: the old sets named another runtime's recipe objects.
                owner = new WeakReference<>(jeiRuntime);
                hiddenBrewing = JeiHiding.identitySet();
                hiddenAnvil = JeiHiding.identitySet();
            }
            passGeneration = -1L;
            pass(jeiRuntime);
        });
    }

    /**
     * JEI stopped, or the client left a server: forgets the active runtime and keeps what was
     * hidden on it, so the same runtime coming back is diffed, not filtered blind. Idempotent.
     */
    public static void clearRuntime() {
        onClientThread("runtime stop", () -> runtime = null);
    }

    /**
     * The synced blacklist changed: one pass on the active runtime, skipped when neither the
     * runtime nor the snapshot changed since the last complete pass.
     */
    public static void refilter() {
        onClientThread("refilter", () -> {
            if (runtime != null) {
                pass(runtime);
            }
        });
    }

    /** One pass over both categories, from one read of the effective snapshot. */
    private static void pass(IJeiRuntime current) {
        BlacklistSnapshot snapshot = Blacklist.effective();
        long generation = snapshot.generation();
        if (generation == passGeneration) {
            return;
        }
        if (snapshot.isEmpty() && hiddenBrewing.isEmpty() && hiddenAnvil.isEmpty()) {
            // Nothing to hide and nothing of ours to show again: no JEI lookup at all, the
            // common case of a server without the mod and of a start before the payload.
            passGeneration = generation;
            return;
        }
        JeiHiding.Plan<IJeiBrewingRecipe> brewing = JeiHiding.plan(JeiRecipes.brewing(current),
                JeiRecipes.visibleBrewing(current),
                recipe -> JeiRules.hideBrewing(snapshot, recipe.getPotionOutput(),
                        recipe.getPotionInputs(), recipe.getIngredients()),
                hiddenBrewing);
        if (!brewing.unhide().isEmpty()) {
            JeiRecipes.unhideBrewing(current, brewing.unhide());
        }
        if (!brewing.hide().isEmpty()) {
            JeiRecipes.hideBrewing(current, brewing.hide());
        }
        hiddenBrewing = brewing.hiddenByUs();

        JeiHiding.Plan<IJeiAnvilRecipe> anvil = JeiHiding.plan(JeiRecipes.anvil(current),
                JeiRecipes.visibleAnvil(current),
                recipe -> JeiRules.hideAnvil(snapshot, recipe.getLeftInputs(),
                        recipe.getRightInputs(), recipe.getOutputs()),
                hiddenAnvil);
        if (!anvil.unhide().isEmpty()) {
            JeiRecipes.unhideAnvil(current, anvil.unhide());
        }
        if (!anvil.hide().isEmpty()) {
            JeiRecipes.hideAnvil(current, anvil.hide());
        }
        hiddenAnvil = anvil.hiddenByUs();

        // Only after both halves: an exception above leaves the old value, so the next
        // refilter tries again.
        passGeneration = generation;
        if (!brewing.hide().isEmpty() || !brewing.unhide().isEmpty()
                || !anvil.hide().isEmpty() || !anvil.unhide().isEmpty()) {
            Log.info(LogTag.RECIPE,
                    "JEI: {} brewing and {} anvil recipes hidden, {} and {} shown again",
                    brewing.hide().size(), anvil.hide().size(), brewing.unhide().size(),
                    anvil.unhide().size());
        }
    }

    /**
     * Runs the task on the client thread, at once when already there, else queued: JEI
     * asserts the client thread, and Fabric's disconnect can fire on a netty thread.
     */
    private static void onClientThread(String what, Runnable task) {
        Runnable guarded = () -> {
            try {
                task.run();
            } catch (RuntimeException | LinkageError e) {
                warn(what, e);
            }
        };
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.isSameThread()) {
                guarded.run();
            } else {
                minecraft.execute(guarded);
            }
        } catch (RuntimeException | LinkageError e) {
            warn(what, e);
        }
    }

    /** A missing or changed optional mod costs its filtering, with one WARN and no stack. */
    private static void warn(String what, Throwable e) {
        Log.warn(LogTag.RECIPE, "JEI {} failed, JEI keeps its current filtering: {}", what,
                e.toString());
    }
}
