package net.sievert.item_blacklist.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The mixin config against the source folders, which nothing else compares: the gate knows no
 * line, so a mixin in the wrong folder or behind the wrong gate compiles and fails only on the
 * releases it was not meant for (CLAUDE.md, "A mixin only from X or only below X"). Reads
 * sources, not compiled output.
 */
class MixinConfigLayoutTest {
    private static final String COMMON = "item-blacklist-common";
    private static final String CONFIG = COMMON + "/src/main/resources/item_blacklist.mixins.json";
    private static final String GATED = "item_blacklist:gated";
    /** An open since-gate of the 1.21.x line: its window runs on into 26.x. */
    private static final Pattern OPEN_SINCE_1_21 = Pattern.compile("since1_21_\\d+");
    /** An open until-gate of the 26.x line: its window holds every 1.21.x release. */
    private static final Pattern OPEN_UNTIL_26 = Pattern.compile("until26_\\d+(_\\d+)*");

    private static Path common;
    private static JsonObject config;
    private static String mixinPath;

    @BeforeAll
    static void readConfig() throws IOException {
        Path root = ProjectFiles.root();
        common = root.resolve(COMMON);
        try (Reader reader = Files.newBufferedReader(root.resolve(CONFIG),
                StandardCharsets.UTF_8)) {
            config = JsonParser.parseReader(reader).getAsJsonObject();
        }
        mixinPath = config.get("package").getAsString().replace('.', '/');
    }

    private static List<String> mixins() {
        List<String> out = new ArrayList<>();
        if (config.has("mixins")) {
            for (JsonElement entry : config.getAsJsonArray("mixins")) {
                out.add(entry.getAsString());
            }
        }
        return out;
    }

    private static Map<String, String> gated() {
        Map<String, String> out = new LinkedHashMap<>();
        if (config.has(GATED)) {
            for (Map.Entry<String, JsonElement> entry
                    : config.getAsJsonObject(GATED).entrySet()) {
                out.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return out;
    }

    /** The source of a mixin entry in a folder; "jer.X" is mixin/jer/X.java. */
    private static Path source(String folder, String entry) {
        return common.resolve("src").resolve(folder).resolve("java").resolve(mixinPath)
                .resolve(entry.replace('.', '/') + ".java");
    }

    @Test
    void everyListedMixinIsSharedOrAPairOfTheLines() {
        for (String entry : mixins()) {
            boolean shared = Files.isRegularFile(source("main", entry));
            boolean pair = Files.isRegularFile(source("until26_1", entry))
                    && Files.isRegularFile(source("since26_1", entry));
            assertTrue(shared || pair, entry + " is listed under mixins, so it must be in "
                    + "src/main or in both src/until26_1 and src/since26_1");
        }
    }

    @Test
    void everyGatedMixinSitsInTheFolderItNames() {
        for (Map.Entry<String, String> entry : gated().entrySet()) {
            assertTrue(Files.isRegularFile(source(entry.getValue(), entry.getKey())),
                    entry.getKey() + " is gated " + entry.getValue() + ", so it must be in src/"
                            + entry.getValue());
        }
    }

    @Test
    void everyOpenSinceGateOfThe121LineHasItsTwin() {
        for (Map.Entry<String, String> entry : gated().entrySet()) {
            if (OPEN_SINCE_1_21.matcher(entry.getValue()).matches()) {
                assertTrue(Files.isRegularFile(source("since26_1", entry.getKey())),
                        entry.getKey() + " is gated " + entry.getValue() + ", which holds 26.x"
                                + " too, so a twin of that name must be in src/since26_1");
            }
        }
    }

    /**
     * The twin is the same source as its src/sinceX class (the form valid from X on 1.21.x and
     * on all of 26.x): the 26.x half is otherwise checked by boots only, so an edit of one half
     * would drift silently. A mixin whose target differs between 26.1.2 and 26.2 is no twin:
     * its 1.21.x class closes at 26_1 instead, and 26.x gets a pair of its own.
     */
    @Test
    void everyTwinIsTheSameSource() throws IOException {
        for (Map.Entry<String, String> entry : gated().entrySet()) {
            if (OPEN_SINCE_1_21.matcher(entry.getValue()).matches()) {
                Path twin = source("since26_1", entry.getKey());
                if (Files.isRegularFile(twin)) {
                    assertEquals(Files.readString(source(entry.getValue(), entry.getKey()),
                                    StandardCharsets.UTF_8),
                            Files.readString(twin, StandardCharsets.UTF_8),
                            "src/since26_1 holds " + entry.getKey() + " as src/"
                                    + entry.getValue() + " does, byte for byte");
                }
            }
        }
    }

    @Test
    void noGateTheGateCannotKeep() {
        for (Map.Entry<String, String> entry : gated().entrySet()) {
            String gate = entry.getValue();
            assertFalse(gate.equals("since26_1"), entry.getKey()
                    + ": a class of src/since26_1 is in no 1.21.x jar, so it cannot be gated");
            assertFalse(OPEN_UNTIL_26.matcher(gate).matches(), entry.getKey() + " is gated "
                    + gate + ", which would hold every 1.21.x release");
        }
    }

    @Test
    void noClientOrServerList() {
        assertFalse(config.has("client"), "one list for both sides: mixins");
        assertFalse(config.has("server"), "one list for both sides: mixins");
    }

    @Test
    void bothListsAreSorted() {
        List<String> mixins = mixins();
        List<String> sortedMixins = new ArrayList<>(mixins);
        sortedMixins.sort(null);
        assertEquals(sortedMixins, mixins, "mixins in String order");
        List<String> gated = new ArrayList<>(gated().keySet());
        List<String> sortedGated = new ArrayList<>(gated);
        sortedGated.sort(null);
        assertEquals(sortedGated, gated, GATED + " in String order");
    }
}
