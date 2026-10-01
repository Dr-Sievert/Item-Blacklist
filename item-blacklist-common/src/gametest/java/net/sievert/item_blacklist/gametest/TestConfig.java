package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.config.ConfigParser;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * The config fixture as the running servers' config: both loaders' test mods call install()
 * at their init, so every GameTest run, from run.py, Gradle or the IDE, sees the same
 * blacklist and no server folder is written by a test. The fixture ships in the test jar only.
 */
public final class TestConfig {
    private static volatile BlacklistConfig fixture;

    private TestConfig() {
    }

    /**
     * The fixture, parsed once: a WARN per warning (there must be none), an ERROR and the empty
     * config when the test jar lacks it, so the run fails in its log.
     */
    public static BlacklistConfig fixture() {
        BlacklistConfig known = fixture;
        if (known != null) {
            return known;
        }
        BlacklistConfig parsed = read();
        fixture = parsed;
        return parsed;
    }

    /** Hands the fixture to the mod: every server starting takes it instead of the file. */
    public static void install() {
        BlacklistConfig config = fixture();
        ItemBlacklistMod.overrideConfig(() -> config);
    }

    private static BlacklistConfig read() {
        // A class reads its own jar's resources whatever the module layer opens; the context
        // class loader is the second source.
        InputStream in = TestConfig.class.getResourceAsStream("/" + Fixtures.CONFIG_RESOURCE);
        if (in == null) {
            in = Thread.currentThread().getContextClassLoader()
                    .getResourceAsStream(Fixtures.CONFIG_RESOURCE);
        }
        if (in == null) {
            Log.error(LogTag.CONFIG, "The GameTest fixture {} is missing from the test jar",
                    Fixtures.CONFIG_RESOURCE);
            return BlacklistConfig.EMPTY;
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            ConfigParser.Result result = ConfigParser.parse(reader);
            for (String warning : result.warnings()) {
                Log.warn(LogTag.CONFIG, "GameTest fixture: {}", warning);
            }
            return result.config();
        } catch (IOException e) {
            Log.error(LogTag.CONFIG,
                    "Cannot read the GameTest fixture " + Fixtures.CONFIG_RESOURCE, e);
            return BlacklistConfig.EMPTY;
        }
    }
}
