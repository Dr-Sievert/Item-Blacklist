package net.sievert.item_blacklist.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The JEI boundary (CLAUDE.md, "JEI"): only the JEI classes name JEI, and only the
 * three plugin classes carry JEI's plugin annotation, since NeoForge's JEI instantiates every
 * class that carries it. The hook, the rules and the diff name nothing of JEI or of the
 * client, since servers load them. Each plugin keeps its uid in a static field, so the plugin
 * of the other window fails with a LinkageError in its initializer, which every JEI finder
 * catches. Reads the sources that ship (src/main, src/gametest and the windowed and line
 * folders of the three parts); src/test is left out, since this class holds the searched
 * texts as literals.
 */
class JeiBoundaryTest {
    private static final List<String> PARTS = List.of(
            "item-blacklist-common", "item-blacklist-fabric", "item-blacklist-neoforge");

    private static final String JEI =
            "item-blacklist-common/src/%s/java/net/sievert/item_blacklist/integration/jei/";

    /** The three plugin classes, to the id class their uid field holds. */
    private static final Map<String, String> PLUGINS = Map.of(
            jei("until1_21_11", "line/ItemBlacklistJeiPluginUntil1_21_11"), "ResourceLocation",
            jei("since1_21_11", "line/ItemBlacklistJeiPluginSince1_21_11"), "Identifier",
            jei("since26_1", "line/ItemBlacklistJeiPlugin"), "Identifier");

    /** The JEI classes of CLAUDE.md, "JEI", that name JEI; ClientBoundaryTest exempts these. */
    private static final Set<String> JEI_CLASSES = Set.of(
            jei("main", "JeiBridge"),
            jei("until26_1", "line/JeiRecipes"),
            jei("since26_1", "line/JeiRecipes"),
            jei("until1_21_4", "line/JeiRecipesUntil1_21_4"),
            jei("since1_21_4", "line/JeiRecipesSince1_21_4"),
            jei("until1_21_11", "line/ItemBlacklistJeiPluginUntil1_21_11"),
            jei("since1_21_11", "line/ItemBlacklistJeiPluginSince1_21_11"),
            jei("since26_1", "line/ItemBlacklistJeiPlugin"),
            jei("until26_1", "line/JeiPluginEntry"),
            jei("since26_1", "line/JeiPluginEntry"));

    /** The classes a server loads on the JEI path; they must name nothing of JEI or a client. */
    private static final Set<String> SERVER_SIDE = Set.of(
            jei("main", "JeiHook"), jei("main", "JeiRules"), jei("main", "JeiHiding"));

    @Test
    void jeiNamedOnlyByJeiClasses() throws IOException {
        assertEquals(new TreeSet<>(JEI_CLASSES), filesWhoseCode("mezz.jei"),
                "the sources whose code names mezz.jei");
    }

    @Test
    void jeiPluginAnnotationOnlyOnThePlugins() throws IOException {
        assertEquals(new TreeSet<>(PLUGINS.keySet()), filesWhoseCode("@JeiPlugin"),
                "the sources whose code carries @JeiPlugin");
    }

    @Test
    void hookAndRulesNameNothingOfJei() throws IOException {
        Path root = ProjectFiles.root();
        for (String file : SERVER_SIDE) {
            Path source = root.resolve(file);
            assertTrue(Files.isRegularFile(source), file + " exists");
            String text = Files.readString(source, StandardCharsets.UTF_8);
            assertFalse(text.contains("mezz"), file + " names mezz");
            assertFalse(text.contains("net.minecraft.client"), file + " names client code");
        }
    }

    @Test
    void pluginUidIsStatic() throws IOException {
        Path root = ProjectFiles.root();
        for (Map.Entry<String, String> plugin : PLUGINS.entrySet()) {
            Path source = root.resolve(plugin.getKey());
            assertTrue(Files.isRegularFile(source), plugin.getKey() + " exists");
            String text = Files.readString(source, StandardCharsets.UTF_8);
            assertTrue(text.contains("private static final " + plugin.getValue() + " UID ="),
                    plugin.getKey() + " keeps its uid in a private static final "
                            + plugin.getValue() + " field UID");
            assertTrue(text.contains("public " + plugin.getValue() + " getPluginUid() {"),
                    plugin.getKey() + " overrides getPluginUid with " + plugin.getValue());
            assertTrue(text.contains("return UID;"), plugin.getKey() + " returns UID");
        }
    }

    /**
     * The shipped sources, relative to the root, holding the text outside a comment line: a
     * Javadoc may name what the code must not.
     */
    private static Set<String> filesWhoseCode(String searched) throws IOException {
        Path root = ProjectFiles.root();
        Set<String> found = new TreeSet<>();
        int read = 0;
        for (Path source : shippedSources(root)) {
            read++;
            for (String line : Files.readAllLines(source, StandardCharsets.UTF_8)) {
                String text = line.strip();
                if (text.startsWith("//") || text.startsWith("*") || text.startsWith("/*")) {
                    continue;
                }
                if (text.contains(searched)) {
                    found.add(root.relativize(source).toString().replace('\\', '/'));
                    break;
                }
            }
        }
        assertFalse(read == 0, "no source found under " + root);
        return found;
    }

    /**
     * Every .java under src/<folder>/java and src/<folder>/gametest/java of the three parts,
     * for every folder but src/test: src/main, src/gametest, the windows and the line folders.
     */
    private static List<Path> shippedSources(Path root) throws IOException {
        Map<String, Path> sources = new TreeMap<>();
        for (String part : PARTS) {
            Path src = root.resolve(part).resolve("src");
            if (!Files.isDirectory(src)) {
                continue;
            }
            List<Path> folders;
            try (Stream<Path> list = Files.list(src)) {
                folders = list.filter(Files::isDirectory)
                        .filter(folder -> !folder.getFileName().toString().equals("test"))
                        .toList();
            }
            for (Path folder : folders) {
                for (Path javaRoot : List.of(folder.resolve("java"),
                        folder.resolve("gametest").resolve("java"))) {
                    if (!Files.isDirectory(javaRoot)) {
                        continue;
                    }
                    try (Stream<Path> walk = Files.walk(javaRoot)) {
                        walk.filter(p -> p.toString().endsWith(".java"))
                                .filter(Files::isRegularFile)
                                .forEach(p -> sources.put(p.toString(), p));
                    }
                }
            }
        }
        return List.copyOf(sources.values());
    }

    /** A JEI source of the common part: folder under src, path under integration/jei. */
    private static String jei(String folder, String path) {
        return String.format(JEI, folder) + path + ".java";
    }
}
