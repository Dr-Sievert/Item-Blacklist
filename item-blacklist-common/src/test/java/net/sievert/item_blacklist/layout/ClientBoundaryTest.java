package net.sievert.item_blacklist.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * No class a server loads names a client class (CLAUDE.md, "Rules that are load-bearing"): a
 * dedicated server lacks them, and the JVM may load what a class names when it verifies it.
 * Only the client packages, the two loader client entry classes inside them, and the JEI
 * classes that name JEI (which only a client with JEI loads) may name client code: in an
 * import, and as a fully qualified name in a signature, a field, a body, a lambda or a catch
 * clause. Reads the code of every source of the three parts, comments and string literals left
 * out (a string names nothing the JVM loads when it verifies the class).
 */
class ClientBoundaryTest {
    private static final List<String> PARTS = List.of(
            "item-blacklist-common", "item-blacklist-fabric", "item-blacklist-neoforge");

    /** The client packages: their classes may import client code. */
    private static final List<String> CLIENT_PACKAGES = List.of(
            "/net/sievert/item_blacklist/client/",
            "/net/sievert/item_blacklist/fabric/client/",
            "/net/sievert/item_blacklist/neoforge/client/");

    /** The JEI classes that name JEI (CLAUDE.md, "JEI"), relative to the common part. */
    private static final String JEI = "java/net/sievert/item_blacklist/integration/jei/";
    private static final Set<String> JEI_CLASSES = Set.of(
            "src/main/" + JEI + "JeiBridge.java",
            "src/until26_1/" + JEI + "line/JeiRecipes.java",
            "src/since26_1/" + JEI + "line/JeiRecipes.java",
            "src/until1_21_4/" + JEI + "line/JeiRecipesUntil1_21_4.java",
            "src/since1_21_4/" + JEI + "line/JeiRecipesSince1_21_4.java",
            "src/until1_21_11/" + JEI + "line/ItemBlacklistJeiPluginUntil1_21_11.java",
            "src/since1_21_11/" + JEI + "line/ItemBlacklistJeiPluginSince1_21_11.java",
            "src/since26_1/" + JEI + "line/ItemBlacklistJeiPlugin.java",
            "src/until26_1/" + JEI + "line/JeiPluginEntry.java",
            "src/since26_1/" + JEI + "line/JeiPluginEntry.java");

    /** What no other class may import: the game's, the loaders' and the mod's client code. */
    private static final List<String> CLIENT_CODE = List.of(
            "net.minecraft.client.",
            "net.sievert.item_blacklist.client.",
            "net.sievert.item_blacklist.fabric.client.",
            "net.sievert.item_blacklist.neoforge.client.",
            "net.fabricmc.fabric.api.client.",
            "net.neoforged.neoforge.client.");

    @Test
    void onlyClientClassesNameClientCode() throws IOException {
        Path root = ProjectFiles.root();
        List<String> offences = new ArrayList<>();
        int read = 0;
        for (String part : PARTS) {
            Path partDir = root.resolve(part);
            Path src = partDir.resolve("src");
            if (!Files.isDirectory(src)) {
                continue;
            }
            List<Path> sources;
            try (Stream<Path> walk = Files.walk(src)) {
                sources = walk.filter(p -> p.toString().endsWith(".java"))
                        .filter(Files::isRegularFile)
                        .toList();
            }
            for (Path source : sources) {
                String relative = partDir.relativize(source).toString().replace('\\', '/');
                if (exempt(part, relative)) {
                    continue;
                }
                read++;
                String[] lines = code(Files.readString(source, StandardCharsets.UTF_8))
                        .split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    String named = clientCode(lines[i]);
                    if (named != null) {
                        offences.add(part + "/" + relative + ":" + (i + 1) + " names "
                                + named);
                    }
                }
            }
        }
        assertFalse(read == 0, "no source found under " + root);
        assertEquals(List.of(), offences);
    }

    private static boolean exempt(String part, String relative) {
        for (String clientPackage : CLIENT_PACKAGES) {
            if (("/" + relative).contains(clientPackage)) {
                return true;
            }
        }
        return part.equals("item-blacklist-common") && JEI_CLASSES.contains(relative);
    }

    /** The first client prefix the line names as a whole name (not inside another), or null. */
    private static String clientCode(String line) {
        for (String prefix : CLIENT_CODE) {
            int at = line.indexOf(prefix);
            while (at >= 0) {
                if (at == 0 || !isNamePart(line.charAt(at - 1))) {
                    return prefix;
                }
                at = line.indexOf(prefix, at + 1);
            }
        }
        return null;
    }

    private static boolean isNamePart(char c) {
        return Character.isJavaIdentifierPart(c) || c == '.';
    }

    /**
     * The source with its comments, string literals, text blocks and character literals
     * blanked out; line breaks stay, so line numbers still match.
     */
    static String code(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (source.startsWith("//", i)) {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                blank(out, source, i, end);
                i = end;
            } else if (source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                blank(out, source, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int end = i + 1;
                while (end < n && source.charAt(end) != c && source.charAt(end) != '\n') {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(end + 1, n);
                blank(out, source, i, end);
                i = end;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /** Appends source[from, to) as spaces, keeping its line breaks. */
    private static void blank(StringBuilder out, String source, int from, int to) {
        for (int k = from; k < to; k++) {
            out.append(source.charAt(k) == '\n' ? '\n' : ' ');
        }
    }
}
