package net.sievert.item_blacklist.platform;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.report.Recorder;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.PotionBrewing;

/**
 * What the game logic asks of the loader. Each loader part ships one implementation, found
 * through META-INF/services ({@link Services}), and each extends {@link FloorPlatform}.
 */
public interface Platform {
    /** The loader's name, "Fabric" or "NeoForge". */
    String loaderName();

    /** Whether the game runs from a dev run (the IDE, Gradle) rather than a production server. */
    boolean isDevelopmentEnvironment();

    /**
     * The running Minecraft release, e.g. "1.21.6", from the loader once the mod loads: the
     * probe Backends picks backends by. The mixin gate runs before that and reads version.json
     * instead (ItemBlacklistMixinPlugin#runningRelease).
     */
    String minecraftVersion();

    /** Every capability the running release has. */
    Set<Capability> capabilities();

    /** Whether the running release has {@code capability}. */
    default boolean supports(Capability capability) {
        return capabilities().contains(capability);
    }

    /**
     * The loader's config folder, where the mod's config file lives: each loader decides where
     * it is (FabricLoader.getConfigDir, FMLPaths.CONFIGDIR), so shared code asks here.
     */
    Path configDir();

    /**
     * Whether a mod with this id is loaded: the guard in front of every use of an optional
     * mod's API (CLAUDE.md, "An optional dependency on another mod").
     */
    boolean isModLoaded(String modId);

    /**
     * Filters the loader's own brewing recipes of this PotionBrewing from kept originals, after
     * the vanilla mixes: NeoForge keeps recipes of its own beside them, Fabric API feeds the
     * vanilla lists and has none. Called by Lifecycle's static filters and on clients.
     */
    void filterLoaderBrewing(PotionBrewing brewing, BlacklistSnapshot snapshot, Recorder recorder);

    /**
     * Sends the payload to the player only when the player's connection has its channel;
     * whether it sent. The guard lets a client without the mod join: NeoForge throws for a
     * player without the channel, and each loader asks its own networking whether it is there.
     */
    boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
}
