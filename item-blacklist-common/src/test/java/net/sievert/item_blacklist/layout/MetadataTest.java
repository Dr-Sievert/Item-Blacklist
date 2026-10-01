package net.sievert.item_blacklist.layout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Both loaders' metadata files, the icon and the form of the entry classes. No boot checks
 * them: a server never invokes the Fabric client entrypoint or JEI's, so a typo there, a
 * missing icon or a lost field would ship unnoticed. Reads sources, not compiled output.
 */
class MetadataTest {
    private static final String COMMON = "item-blacklist-common";
    private static final String FABRIC = "item-blacklist-fabric";
    private static final String NEOFORGE = "item-blacklist-neoforge";
    private static final String FABRIC_MOD_JSON = FABRIC + "/src/main/resources/fabric.mod.json";
    private static final String MODS_TOML =
            NEOFORGE + "/src/main/resources/META-INF/neoforge.mods.toml";
    private static final String ICON = COMMON + "/src/main/resources/item_blacklist.png";
    private static final String NEOFORGE_CLIENT = NEOFORGE
            + "/src/main/java/net/sievert/item_blacklist/neoforge/client/"
            + "ItemBlacklistNeoForgeClient.java";
    private static final String JEI_ENTRYPOINT =
            "net.sievert.item_blacklist.integration.jei.line.JeiPluginEntry::PLUGIN";
    private static final String DEPENDENCIES = "[[dependencies.${mod_id}]]";

    /**
     * Forms no shipped source may hold, written here as they would appear: NeoForge's
     * EventBusSubscriber, whose bus argument is gone on later releases, and FMLEnvironment.dist
     * (CLAUDE.md, "Rules that are load-bearing"); the annotation OnlyIn, since the client
     * boundary is kept by packages (same section); and a removal warning silenced rather than
     * fixed, which would hide what the version modules' -Xlint:removal catches.
     */
    private static final List<String> BANNED = List.of("@OnlyIn", "EventBusSubscriber",
            "FMLEnvironment.dist", "@SuppressWarnings(\"removal\")");

    private static final byte[] PNG_SIGNATURE =
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    @Test
    void fabricEntrypointsResolve() throws IOException {
        Path root = ProjectFiles.root();
        JsonObject entrypoints = fabricModJson(root).getAsJsonObject("entrypoints");
        assertNotNull(entrypoints, "fabric.mod.json has entrypoints");
        assertEquals(Set.of("main", "client", "jei_mod_plugin"), entrypoints.keySet(),
                "the entrypoints' keys");
        assertEquals(List.of("net.sievert.item_blacklist.fabric.ItemBlacklistFabric"),
                strings(entrypoints.get("main")), "the main entrypoint");
        assertEquals(List.of("net.sievert.item_blacklist.fabric.client.ItemBlacklistFabricClient"),
                strings(entrypoints.get("client")), "the client entrypoint");
        assertEquals(List.of(JEI_ENTRYPOINT), strings(entrypoints.get("jei_mod_plugin")),
                "the jei_mod_plugin entrypoint");
        for (Map.Entry<String, JsonElement> entry : entrypoints.entrySet()) {
            for (String value : strings(entry.getValue())) {
                int member = value.indexOf("::");
                String className = member < 0 ? value : value.substring(0, member);
                assertResolves(root, entry.getKey(), className);
            }
        }
    }

    @Test
    void fabricMetadataFields() throws IOException {
        JsonObject json = fabricModJson(ProjectFiles.root());
        assertEquals(List.of("${mod_id}.mixins.json"), strings(json.get("mixins")), "mixins");
        assertEquals("${mod_id}.png", string(json, "icon"), "icon");
        assertEquals(List.of("${mod_author}"), strings(json.get("authors")), "authors");
        JsonObject contact = json.getAsJsonObject("contact");
        assertNotNull(contact, "fabric.mod.json has contact");
        assertEquals("${mod_sources}", string(contact, "sources"), "contact.sources");
        assertEquals("${mod_issues}", string(contact, "issues"), "contact.issues");
        JsonObject suggests = json.getAsJsonObject("suggests");
        assertNotNull(suggests, "fabric.mod.json has suggests");
        assertEquals(Set.of("jei", "jeresources"), suggests.keySet(), "suggests' keys");
        assertEquals("*", string(suggests, "jei"), "suggests.jei");
        assertEquals("*", string(suggests, "jeresources"), "suggests.jeresources");
        JsonObject depends = json.getAsJsonObject("depends");
        assertNotNull(depends, "fabric.mod.json has depends");
        assertFalse(depends.has("jei"), "JEI is optional, never in depends");
        assertFalse(depends.has("jeresources"), "JER is optional, never in depends");
    }

    @Test
    void neoforgeMetadataFields() throws IOException {
        List<Block> blocks = tomlBlocks(ProjectFiles.root().resolve(MODS_TOML));
        Block top = blocks.get(0);
        assertEquals("", top.header(), "the file starts with its top-level keys");
        assertEquals("${mod_license}", top.values().get("license"), "license");
        assertEquals("${mod_issues}", top.values().get("issueTrackerURL"), "issueTrackerURL");

        List<Block> mods = headed(blocks, "[[mods]]");
        assertEquals(1, mods.size(), "one [[mods]] block");
        Map<String, String> mod = mods.get(0).values();
        assertEquals("${mod_id}.png", mod.get("logoFile"), "logoFile");
        assertEquals("${mod_author}", mod.get("authors"), "authors");
        assertEquals("${mod_sources}", mod.get("displayURL"), "displayURL");

        List<Block> mixins = headed(blocks, "[[mixins]]");
        assertEquals(1, mixins.size(), "one [[mixins]] block: one mixin config");
        assertEquals("${mod_id}.mixins.json", mixins.get(0).values().get("config"),
                "the mixin config");

        Map<String, Integer> seen = new HashMap<>();
        for (Block dependency : headed(blocks, DEPENDENCIES)) {
            Map<String, String> values = dependency.values();
            String modId = values.get("modId");
            assertNotNull(modId, "every dependency block names a modId");
            seen.merge(modId, 1, Integer::sum);
            switch (modId) {
                case "neoforge", "minecraft" -> {
                    assertEquals("required", values.get("type"), modId + " type");
                    assertEquals("BOTH", values.get("side"), modId + " side");
                }
                case "jei", "jeresources" -> {
                    assertEquals("optional", values.get("type"), modId + " type");
                    assertEquals("[0,)", values.get("versionRange"), modId + " versionRange");
                    assertEquals("NONE", values.get("ordering"), modId + " ordering");
                    assertEquals("CLIENT", values.get("side"), modId + " side");
                }
                default -> throw new AssertionError("unexpected dependency " + modId);
            }
        }
        assertEquals(Map.of("neoforge", 1, "minecraft", 1, "jei", 1, "jeresources", 1), seen,
                "each dependency exactly once");
    }

    @Test
    void iconIsA128PixelPng() throws IOException {
        Path icon = ProjectFiles.root().resolve(ICON);
        assertTrue(Files.isRegularFile(icon), ICON + " exists");
        byte[] bytes = Files.readAllBytes(icon);
        assertTrue(bytes.length >= 24, "the icon holds a PNG header");
        assertArrayEquals(PNG_SIGNATURE, Arrays.copyOf(bytes, 8), "the PNG signature");
        ByteBuffer header = ByteBuffer.wrap(bytes);
        assertEquals(128, header.getInt(16), "the icon's width");
        assertEquals(128, header.getInt(20), "the icon's height");
    }

    @Test
    void neoforgeClientClassIsClientDist() throws IOException {
        Path source = ProjectFiles.root().resolve(NEOFORGE_CLIENT);
        assertTrue(Files.isRegularFile(source), NEOFORGE_CLIENT + " exists");
        assertTrue(Files.readString(source, StandardCharsets.UTF_8).contains("dist = Dist.CLIENT"),
                "the NeoForge client class is a @Mod of dist = Dist.CLIENT");
    }

    @Test
    void noBannedLoaderAnnotations() throws IOException {
        Path root = ProjectFiles.root();
        List<String> offences = new ArrayList<>();
        int read = 0;
        for (String part : List.of(COMMON, FABRIC, NEOFORGE)) {
            Path src = root.resolve(part).resolve("src");
            if (!Files.isDirectory(src)) {
                continue;
            }
            List<Path> sources;
            try (Stream<Path> walk = Files.walk(src)) {
                // src/test is left out: this class holds the banned forms as literals.
                sources = walk.filter(p -> p.toString().endsWith(".java"))
                        .filter(Files::isRegularFile)
                        .filter(p -> !src.relativize(p).startsWith("test"))
                        .toList();
            }
            for (Path source : sources) {
                read++;
                List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    String text = lines.get(i).strip();
                    if (text.startsWith("//") || text.startsWith("*") || text.startsWith("/*")) {
                        continue;
                    }
                    for (String banned : BANNED) {
                        if (text.contains(banned)) {
                            offences.add(root.relativize(source).toString().replace('\\', '/')
                                    + ":" + (i + 1) + " holds " + banned);
                        }
                    }
                }
            }
        }
        assertFalse(read == 0, "no source found under " + root);
        assertEquals(List.of(), offences);
    }

    private static JsonObject fabricModJson(Path root) throws IOException {
        // Every ${...} token sits inside a string, so the unexpanded file parses as it is.
        try (Reader reader = Files.newBufferedReader(root.resolve(FABRIC_MOD_JSON),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** A string, or each string of an array (fabric.mod.json allows both for most fields). */
    private static List<String> strings(JsonElement element) {
        assertNotNull(element, "a field that must exist");
        List<String> out = new ArrayList<>();
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                out.add(item.getAsString());
            }
        } else {
            out.add(element.getAsString());
        }
        return out;
    }

    private static String string(JsonObject object, String key) {
        JsonElement element = object.get(key);
        assertNotNull(element, key + " exists");
        return element.getAsString();
    }

    /**
     * An entrypoint class must sit where every release's jar has it, and nowhere else: in
     * src/main of the Fabric or the common part, or as a line pair in src/until26_1 and
     * src/since26_1 of one of them. A windowed folder would ship it to some releases only.
     */
    private static void assertResolves(Path root, String entrypoint, String className)
            throws IOException {
        String file = className.replace('.', '/') + ".java";
        Set<String> found = new TreeSet<>();
        for (String part : List.of(COMMON, FABRIC)) {
            Path src = root.resolve(part).resolve("src");
            if (!Files.isDirectory(src)) {
                continue;
            }
            try (Stream<Path> folders = Files.list(src)) {
                for (Path folder : folders.toList()) {
                    if (Files.isRegularFile(folder.resolve("java").resolve(file))) {
                        found.add(part + ":" + folder.getFileName());
                    }
                }
            }
        }
        boolean valid = found.equals(Set.of(FABRIC + ":main"))
                || found.equals(Set.of(COMMON + ":main"))
                || found.equals(Set.of(FABRIC + ":until26_1", FABRIC + ":since26_1"))
                || found.equals(Set.of(COMMON + ":until26_1", COMMON + ":since26_1"));
        assertTrue(valid, "entrypoint " + entrypoint + " names " + className
                + ", whose source must be in src/main or in both line folders of one part, and"
                + " nowhere else; found in " + found);
    }

    /** The file's blocks: the top-level keys first (header ""), then one per [[...]] line. */
    private static List<Block> tomlBlocks(Path toml) throws IOException {
        List<Block> blocks = new ArrayList<>();
        Block current = new Block("", new LinkedHashMap<>());
        blocks.add(current);
        for (String raw : Files.readAllLines(toml, StandardCharsets.UTF_8)) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[[")) {
                current = new Block(line, new LinkedHashMap<>());
                blocks.add(current);
                continue;
            }
            int equals = line.indexOf('=');
            assertTrue(equals > 0, "a key = value line: " + line);
            String key = line.substring(0, equals).strip();
            String value = line.substring(equals + 1).strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            assertFalse(current.values().containsKey(key), "a key twice in one block: " + key);
            current.values().put(key, value);
        }
        return blocks;
    }

    private static List<Block> headed(List<Block> blocks, String header) {
        List<Block> out = new ArrayList<>();
        for (Block block : blocks) {
            if (block.header().equals(header)) {
                out.add(block);
            }
        }
        return out;
    }

    /** One TOML block: its [[...]] line ("" for the top level) and its key = value lines. */
    private record Block(String header, Map<String, String> values) {
    }
}
