package net.sievert.item_blacklist.mixinplugin;

import net.sievert.item_blacklist.ItemBlacklistMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The own-targets mixin check, instead of Mixin's global audit(), which would also load
 * Fabric API's and NeoForge's targets. Loads each target the mixin plugin recorded, by name;
 * a failed injection surfaces here as an error naming the target. Then each gated mixin whose
 * window contains the running release must have been applied, and the plugin's gate must have
 * found no problem. It runs at server start on every server, the shipped mod's too, so a
 * broken injection shows at start instead of at first use; its ERROR line fails
 * scripts/run.py's smoke boot and GameTest run alike.
 *
 * <p>The optional target of a mixin marked @Pseudo is never loaded here: it is another mod's
 * class, which may name client classes, and the plugin never records it. The audit logs which
 * of those mixins applied instead, so a renamed class that silently disables one shows.
 */
public final class MixinAudit {
    private MixinAudit() {
    }

    /**
     * Logs the verdict: INFO when every own target loads and every gated mixin in its window
     * applied, else ERROR with one entry per problem. Then, when the config has @Pseudo
     * mixins, one INFO line with those applied so far out of all of them.
     */
    public static void run() {
        List<String> problems = new ArrayList<>(ItemBlacklistMixinPlugin.problems());
        ClassLoader loader = MixinAudit.class.getClassLoader();
        Set<String> targets = ItemBlacklistMixinPlugin.targets();
        if (targets.isEmpty() && loader.getResource(ItemBlacklistMixinPlugin.CONFIG) != null) {
            problems.add(ItemBlacklistMixinPlugin.CONFIG
                    + " is present but its plugin recorded no targets");
        }
        for (String target : targets) {
            try {
                Class.forName(target, false, loader);
            } catch (Throwable t) {
                problems.add(target + ": " + t);
            }
        }
        Set<String> gated = ItemBlacklistMixinPlugin.gatedIn();
        Set<String> applied = ItemBlacklistMixinPlugin.applied();
        for (String mixin : gated) {
            if (!applied.contains(mixin)) {
                problems.add("gated mixin " + mixin + " is in its window but was not applied");
            }
        }
        if (problems.isEmpty()) {
            ItemBlacklistMod.LOG.info(ItemBlacklistMod.NAME
                            + " mixin audit: {} own target(s) loaded, {} gated mixin(s) applied"
                            + " in their window, no problems: {} {}",
                    targets.size(), gated.size(), targets, gated);
        } else {
            ItemBlacklistMod.LOG.error(ItemBlacklistMod.NAME + " mixin audit failed: {}", problems);
        }
        Set<String> optional = new TreeSet<>(ItemBlacklistMixinPlugin.pseudoMixins());
        if (!optional.isEmpty()) {
            Set<String> optionalApplied = new TreeSet<>(optional);
            optionalApplied.retainAll(applied);
            ItemBlacklistMod.LOG.info(ItemBlacklistMod.NAME
                    + " mixin audit: optional mixins applied {} of {}", optionalApplied, optional);
        }
    }
}
