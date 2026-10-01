package net.sievert.item_blacklist;

import net.sievert.item_blacklist.blacklist.Lifecycle;
import net.sievert.item_blacklist.config.BlacklistConfig;
import net.sievert.item_blacklist.config.ConfigFile;
import net.sievert.item_blacklist.line.Line;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.mixinplugin.MixinAudit;
import net.sievert.item_blacklist.platform.Platform;
import net.sievert.item_blacklist.platform.Services;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mod's loader-independent entry points: each loader part's own class calls these from
 * its loader's hooks. Game logic starts here.
 */
public final class ItemBlacklistMod {
    /** The mod's id, as the loaders' metadata declare it; it names the logger. */
    public static final String MOD_ID = "item_blacklist";
    /** The mod's name. Every log line of the mod starts with it; scripts/run.py keys on that. */
    public static final String NAME = "Item Blacklist";
    /** The mod's logger, named after its id. */
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    /** The config of the last load; EMPTY until the first and after any failure. */
    private static volatile BlacklistConfig config = BlacklistConfig.EMPTY;
    /** The test mod's config source, read at every server starting when set; else null. */
    private static volatile Supplier<BlacklistConfig> override;

    private ItemBlacklistMod() {
    }

    /**
     * Called once by each loader's entry class, while the mod loads. Reads the config file, or
     * writes the default one, and logs its summary; it touches no registry, since the game's
     * registries are not ready here and NeoForge may call it on a worker thread.
     */
    public static void init() {
        Platform platform = Services.PLATFORM;
        LOG.info(NAME + " initialising on {} ({} environment), {} line jar, Minecraft {},"
                        + " capabilities {}",
                platform.loaderName(),
                platform.isDevelopmentEnvironment() ? "development" : "production",
                Line.NAME,
                platform.minecraftVersion(),
                platform.capabilities());
        config = report(ConfigFile.loadOrCreate(platform.configDir()), true);
    }

    /**
     * Fabric SERVER_STARTING, NeoForge ServerAboutToStartEvent: the server's registries exist,
     * its levels do not; the blacklist is resolved for this server (Lifecycle.starting).
     */
    public static void onServerStarting(MinecraftServer server) {
        Lifecycle.starting(server);
    }

    /**
     * The server finished starting: every mixin target can load now, so the audit runs; then
     * the forced start reload, once per server (Lifecycle.started).
     */
    public static void onServerStarted(MinecraftServer server) {
        LOG.info(NAME + " saw the server start on {}", Services.PLATFORM.loaderName());
        MixinAudit.run();
        Lifecycle.started(server);
    }

    /**
     * Fabric SERVER_STOPPED, NeoForge ServerStoppedEvent: the server's state goes, so nothing of
     * this world reaches the next one in the same JVM (Lifecycle.stopped).
     */
    public static void onServerStopped(MinecraftServer server) {
        Lifecycle.stopped(server);
    }

    /** The config of the last load (mod init, or the last server starting); never null. */
    public static BlacklistConfig config() {
        return config;
    }

    /**
     * The read at server starting, for Lifecycle only: the test mod's override when one is set,
     * else the file, with its warnings logged. Logs the load line when the content differs from
     * what config() held, stores it there and returns it. The config is read at every server
     * start, never on a datapack reload.
     */
    public static BlacklistConfig loadForServer() {
        Supplier<BlacklistConfig> source = override;
        BlacklistConfig next;
        if (source != null) {
            next = Objects.requireNonNullElse(source.get(), BlacklistConfig.EMPTY);
            if (!next.equals(config)) {
                Log.info(LogTag.CONFIG, "Loaded the GameTest config: {}",
                        String.join(", ", next.summary()));
            }
        } else {
            ConfigFile.Loaded loaded = ConfigFile.loadOrCreate(Services.PLATFORM.configDir());
            next = report(loaded, !loaded.config().equals(config));
        }
        config = next;
        return next;
    }

    /**
     * The test mod's seam: when set, every server starting takes this config instead of the
     * file, so every GameTest run, from run.py, Gradle or the IDE, sees the same blacklist.
     * Mod init still reads and creates the file. Production never calls it.
     */
    public static void overrideConfig(Supplier<BlacklistConfig> source) {
        override = source;
    }

    /**
     * Logs what ConfigFile returned: each warning, the created file, the one ERROR of a
     * default that could not be written, and with {@code summary} the Loaded line (mod init
     * always; server starting only when the content changed); returns the config.
     */
    private static BlacklistConfig report(ConfigFile.Loaded loaded, boolean summary) {
        for (String warning : loaded.warnings()) {
            Log.warn(LogTag.CONFIG, "{}", warning);
        }
        if (loaded.created()) {
            Log.info(LogTag.CONFIG, "Created the default config at {}", loaded.path());
        }
        if (loaded.writeFailed()) {
            Log.error(LogTag.CONFIG, "Failed to write the default config at {}", loaded.path());
        }
        if (summary) {
            Log.info(LogTag.CONFIG, "Loaded {}: {}", loaded.path(),
                    String.join(", ", loaded.config().summary()));
        }
        return loaded.config();
    }
}
