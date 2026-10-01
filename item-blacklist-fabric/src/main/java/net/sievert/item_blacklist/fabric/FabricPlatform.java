package net.sievert.item_blacklist.fabric;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.platform.FloorPlatform;
import net.sievert.item_blacklist.report.Recorder;
import java.nio.file.Path;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.PotionBrewing;

/** The platform on Fabric; META-INF/services names it. */
public final class FabricPlatform extends FloorPlatform {
    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    /** From the loader: the same call on every supported release. */
    @Override
    public String minecraftVersion() {
        return FabricLoader.getInstance().getRawGameVersion();
    }

    /** The game folder's config/, as Fabric Loader reports it on every supported release. */
    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    /** Fabric Loader's own answer: it knows every mod of the game before any entrypoint runs. */
    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    /** Nothing: Fabric API's brewing builder feeds the vanilla lists, which BrewingFilter reads. */
    @Override
    public void filterLoaderBrewing(PotionBrewing brewing, BlacklistSnapshot snapshot,
            Recorder recorder) {
    }

    /**
     * Sends through Fabric API when the player's client registered the channel: canSend reads
     * the channels the client declared, which a vanilla client never does. The Type overload
     * of canSend names no id class, so one call serves every release.
     */
    @Override
    public boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null || !ServerPlayNetworking.canSend(player, payload.type())) {
            return false;
        }
        ServerPlayNetworking.send(player, payload);
        return true;
    }
}
