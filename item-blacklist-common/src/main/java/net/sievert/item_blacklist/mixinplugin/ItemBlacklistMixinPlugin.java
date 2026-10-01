package net.sievert.item_blacklist.mixinplugin;

import net.sievert.item_blacklist.line.Releases;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;

/**
 * Companion plugin of item_blacklist.mixins.json: the own-targets check and the gate for
 * version-bound mixins.
 *
 * <p>Own targets: Mixin hands it the targets it resolved for the config's listed mixins
 * (acceptTargets); the plugin compares them with the targets those classes declare in
 * {@code @Mixin} and fails the load, naming the class, when one does not exist. Mixin 0.17.4
 * with Fabric's compatibility level already refuses that for a required config; the 0.15.2
 * and 0.17.3 that NeoForge ships only warn. Throwing from onLoad would not do: Mixin catches
 * it and only warns. The resolved targets are kept for MixinAudit, which loads each one by
 * name at server start. Mixin's own audit() would also load Fabric API's and NeoForge's
 * targets, and its errors name their classes, not ours.
 *
 * <p>Gate: a mixin valid only in a window of releases lives in that window's folder
 * (src/since26_2, src/until1_21_6 ...), so it ships in its line's jar but may be absent from
 * the other line's jar, and names classes or methods other releases lack. It is never listed
 * under "mixins": Mixin reads every listed class before any plugin is asked, and a missing
 * class fails a required config. It is listed in the config's own table
 * {@code "item_blacklist:gated": {"<mixin>": "<window folder>"}} instead, and this plugin hands it
 * to Mixin (getMixins) only when the running release is inside the window. The running
 * release is read from the game jar's version.json before any game class loads. The build
 * (checkLazyVersionClasses) checks that every mixin in a windowed folder is in the table with
 * exactly its folder's window, and that nothing lists it unconditionally. CLAUDE.md, "A mixin
 * only from X or only below X".
 *
 * <p>Optional targets: a mixin marked @Pseudo targets another mod's class. When the class is
 * absent the mixin is neither applied nor an error, and the audit never loads its target (the
 * other mod's classes may name client classes). CLAUDE.md, "An optional dependency on another
 * mod".
 *
 * <p>Lives outside the mixin package: classes there cannot be referenced.
 */
public final class ItemBlacklistMixinPlugin implements IMixinConfigPlugin {
    /** The mixin config this plugin belongs to, as the loaders' metadata name it. */
    public static final String CONFIG = "item_blacklist.mixins.json";
    /** The config's table of version-bound mixins: simple class name to window folder name. */
    public static final String GATED = "item_blacklist:gated";
    private static final Logger LOG = LoggerFactory.getLogger("item_blacklist");
    private static final Set<String> TARGETS = new ConcurrentSkipListSet<>();
    private static final Set<String> GATED_IN = new ConcurrentSkipListSet<>();
    private static final Set<String> APPLIED = new ConcurrentSkipListSet<>();
    private static final Set<String> PSEUDO = new ConcurrentSkipListSet<>();
    private static final List<String> PROBLEMS = new CopyOnWriteArrayList<>();

    private String mixinPackage = "";
    private JsonObject config;
    /** Class name to whether its bytes can be read; asked once per class. */
    private final Map<String, Boolean> classExists = new ConcurrentHashMap<>();
    /** Mixin class name to whether it carries @Pseudo; asked once per mixin. */
    private final Map<String, Boolean> mixinIsPseudo = new ConcurrentHashMap<>();

    /**
     * The targets Mixin resolved for this config, and those of the gated mixins that apply
     * here, as binary class names.
     */
    public static Set<String> targets() {
        return Set.copyOf(TARGETS);
    }

    /** The gated mixins whose window contains the running release (fully qualified). */
    public static Set<String> gatedIn() {
        return Set.copyOf(GATED_IN);
    }

    /** Every mixin class Mixin reported as applied to a target (postApply). */
    public static Set<String> applied() {
        return Set.copyOf(APPLIED);
    }

    /**
     * The listed and gated mixins that carry @Pseudo (fully qualified); their targets are
     * optional, so MixinAudit reports which of them applied instead of loading their targets.
     */
    public static Set<String> pseudoMixins() {
        return Set.copyOf(PSEUDO);
    }

    /** Problems found while gating, for MixinAudit. */
    public static List<String> problems() {
        return List.copyOf(PROBLEMS);
    }

    @Override
    public void onLoad(String mixinPackage) {
        this.mixinPackage = mixinPackage;
        this.config = readConfig();
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    /**
     * False for a @Pseudo mixin whose target cannot be read, before Mixin looks the target up;
     * true for every other mixin. Mixin asks this while it prepares the listed mixins, before
     * acceptTargets and getMixins, so whether the mixin is pseudo is read from its own class.
     */
    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !pseudo(mixinClassName) || exists(targetClassName);
    }

    /**
     * Fails the load when a listed mixin that is not @Pseudo targets a class Mixin could not
     * resolve. The targets of @Pseudo mixins are optional: they are logged, present and
     * absent, and never recorded for MixinAudit.
     */
    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        MixinTargets.Verdict verdict = MixinTargets.classify(declaredTargets(), PSEUDO, myTargets);
        if (!verdict.missing().isEmpty()) {
            String message = "Item Blacklist mixin check: " + CONFIG
                    + " targets classes that do not exist: " + verdict.missing();
            LOG.error(message);
            throw new IllegalStateException(message);
        }
        Set<String> required = new TreeSet<>(myTargets);
        required.removeAll(verdict.optionalPresent());
        required.removeAll(verdict.optionalAbsent());
        TARGETS.addAll(required);
        LOG.info("Item Blacklist mixin check: {} has {} own target(s), all present: {}",
                CONFIG, required.size(), required);
        if (!verdict.optionalPresent().isEmpty() || !verdict.optionalAbsent().isEmpty()) {
            LOG.info("Item Blacklist mixin check: optional target(s) present {}, absent {}",
                    verdict.optionalPresent(), verdict.optionalAbsent());
        }
    }

    /**
     * The gated mixins whose window contains the running release. Mixin prepares them after
     * acceptTargets, so their targets are checked here: a missing one is recorded for the
     * audit (Fabric's Mixin also refuses it by itself). A gated @Pseudo mixin whose target is
     * absent is skipped as no problem; one whose targets exist is handed over, but neither it
     * nor its targets are recorded for the audit, which reports it among the optional mixins.
     */
    @Override
    public List<String> getMixins() {
        List<String> chosen = new ArrayList<>();
        if (!config.has(GATED)) {
            return chosen;
        }
        String running = runningRelease();
        for (Map.Entry<String, JsonElement> entry : config.getAsJsonObject(GATED).entrySet()) {
            String mixinClass = mixinPackage + "." + entry.getKey();
            String window = entry.getValue().getAsString();
            if (!Releases.inFolder(window, running)) {
                LOG.info("Item Blacklist mixin gate: {} ({}) skipped on Minecraft {}",
                        entry.getKey(), window, running);
                continue;
            }
            Map<String, String> targets = new LinkedHashMap<>();
            if (!readTargets(mixinClass, targets)) {
                problem("gated mixin " + mixinClass + " (" + window + ") applies on " + running
                        + " but is not in this jar");
                continue;
            }
            if (PSEUDO.contains(mixinClass)) {
                List<String> absent = new ArrayList<>();
                for (String target : targets.keySet()) {
                    if (!exists(target)) {
                        absent.add(target);
                    }
                }
                if (!absent.isEmpty()) {
                    LOG.info("Item Blacklist mixin gate: {} ({}) skipped on Minecraft {}: optional"
                            + " target {} absent", entry.getKey(), window, running, absent);
                    continue;
                }
                chosen.add(entry.getKey());
                LOG.info("Item Blacklist mixin gate: {} ({}) applies on Minecraft {}, optional"
                        + " target(s) {}", entry.getKey(), window, running, targets.keySet());
                continue;
            }
            for (String target : targets.keySet()) {
                try {
                    MixinService.getService().getBytecodeProvider().getClassNode(target);
                } catch (Exception e) {
                    problem("gated mixin " + mixinClass + " (" + window + ") targets " + target
                            + ", which does not exist on " + running);
                }
            }
            TARGETS.addAll(targets.keySet());
            GATED_IN.add(mixinClass);
            chosen.add(entry.getKey());
            LOG.info("Item Blacklist mixin gate: {} ({}) applies on Minecraft {}, target(s) {}",
                    entry.getKey(), window, running, targets.keySet());
        }
        return chosen;
    }

    @Override
    public void preApply(
            String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(
            String targetClassName, ClassNode targetClass, String mixinClassName,
            IMixinInfo mixinInfo) {
        APPLIED.add(mixinClassName);
    }

    private static void problem(String message) {
        LOG.error("Item Blacklist mixin gate: {}", message);
        PROBLEMS.add(message);
    }

    /**
     * The running release, from version.json at the root of the game jar ("id": "26.2").
     * Read as a resource, before any game class loads: the loaders' own version APIs differ
     * per loader and generation (NeoForge's mod list does not exist yet at this point).
     */
    static String runningRelease() {
        List<ClassLoader> loaders = new ArrayList<>();
        loaders.add(ItemBlacklistMixinPlugin.class.getClassLoader());
        loaders.add(Thread.currentThread().getContextClassLoader());
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                for (URL url : Collections.list(loader.getResources("version.json"))) {
                    try (InputStream in = url.openStream()) {
                        JsonObject json = JsonParser.parseReader(
                                new InputStreamReader(in, StandardCharsets.UTF_8))
                                .getAsJsonObject();
                        if (json.has("id") && json.has("world_version")) {
                            String id = json.get("id").getAsString();
                            LOG.info("Item Blacklist mixin gate: running Minecraft {}"
                                    + " (version.json at {})", id, url);
                            return id;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("Item Blacklist mixin gate: cannot read version.json through {}: {}",
                        loader, e.toString());
            }
        }
        throw new IllegalStateException(
                "Item Blacklist mixin gate: no Minecraft version.json on the class path");
    }

    private static JsonObject readConfig() {
        try (InputStream in =
                ItemBlacklistMixinPlugin.class.getClassLoader().getResourceAsStream(CONFIG)) {
            if (in == null) {
                throw new IllegalStateException("not on the class path");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new IllegalStateException("Item Blacklist mixin check: cannot read " + CONFIG, e);
        }
    }

    /**
     * Target class name to mixin class name, read from the @Mixin annotation of every mixin
     * this config lists for this side.
     */
    private Map<String, String> declaredTargets() {
        String side = MixinEnvironment.getCurrentEnvironment().getSide()
                == MixinEnvironment.Side.CLIENT ? "client" : "server";
        Map<String, String> declared = new TreeMap<>();
        for (String list : List.of("mixins", side)) {
            if (!config.has(list)) {
                continue;
            }
            for (JsonElement entry : config.getAsJsonArray(list)) {
                String mixinClass = mixinPackage + "." + entry.getAsString();
                if (!readTargets(mixinClass, declared)) {
                    throw new IllegalStateException(
                            "Item Blacklist mixin check: cannot read " + mixinClass);
                }
            }
        }
        return declared;
    }

    /**
     * Adds target class name to mixin class name for one mixin class; false when the class is
     * not there. A @Pseudo mixin is recorded as such, and its targets go in only where no
     * other mixin named them, so a target that a required mixin also names stays required.
     */
    private static boolean readTargets(String mixinClass, Map<String, String> into) {
        ClassNode node;
        try {
            node = MixinService.getService().getBytecodeProvider().getClassNode(mixinClass);
        } catch (Exception e) {
            return false;
        }
        AnnotationNode mixin = Annotations.getInvisible(node, Mixin.class);
        if (mixin == null) {
            return true;
        }
        boolean pseudo = carriesPseudo(node);
        if (pseudo) {
            PSEUDO.add(mixinClass);
        }
        List<Type> classes = Annotations.getValue(mixin, "value");
        List<String> names = Annotations.getValue(mixin, "targets");
        List<String> targets = new ArrayList<>();
        if (classes != null) {
            classes.forEach(type -> targets.add(type.getClassName()));
        }
        if (names != null) {
            names.forEach(name -> targets.add(name.replace('/', '.')));
        }
        for (String target : targets) {
            if (pseudo) {
                into.putIfAbsent(target, mixinClass);
            } else {
                into.put(target, mixinClass);
            }
        }
        return true;
    }

    /**
     * Whether the mixin class carries @Pseudo, read from its own class node, since Mixin asks
     * shouldApplyMixin before this plugin has read any config entry; a class that cannot be
     * read is not pseudo. Asked once per mixin; a pseudo one is recorded for the audit.
     */
    private boolean pseudo(String mixinClass) {
        return mixinIsPseudo.computeIfAbsent(mixinClass, name -> {
            boolean pseudo;
            try {
                pseudo = carriesPseudo(
                        MixinService.getService().getBytecodeProvider().getClassNode(name));
            } catch (Exception e) {
                pseudo = false;
            }
            if (pseudo) {
                PSEUDO.add(name);
            }
            return pseudo;
        });
    }

    /**
     * Whether a class's bytes can be read, without loading it: the optional target of a mixin
     * marked @Pseudo is absent when they cannot. Asked once per class.
     */
    private boolean exists(String className) {
        return classExists.computeIfAbsent(className.replace('/', '.'), name -> {
            try {
                MixinService.getService().getBytecodeProvider().getClassNode(name);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    /**
     * Whether a class node carries @Pseudo. Its retention is CLASS, so it is invisible; the
     * visible form is accepted too.
     */
    private static boolean carriesPseudo(ClassNode node) {
        return Annotations.getInvisible(node, Pseudo.class) != null
                || Annotations.getVisible(node, Pseudo.class) != null;
    }
}
