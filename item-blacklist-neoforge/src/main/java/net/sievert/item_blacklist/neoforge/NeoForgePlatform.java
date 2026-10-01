package net.sievert.item_blacklist.neoforge;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.neoforge.line.Fml;
import net.sievert.item_blacklist.platform.FloorPlatform;
import net.sievert.item_blacklist.report.Recorder;
import java.nio.file.Path;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

/** The platform on NeoForge; META-INF/services names it. */
public final class NeoForgePlatform extends FloorPlatform {
    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !Fml.isProduction();
    }

    /** From the mod list's "minecraft" entry: the same call in FML 4 (1.21.1) to FML 11 (26.x). */
    @Override
    public String minecraftVersion() {
        return ModList.get().getModContainerById("minecraft")
                .orElseThrow(() -> new IllegalStateException("no minecraft in the mod list"))
                .getModInfo().getVersion().toString();
    }

    /** FML's config folder: the same enum constant and call in FML 4 (1.21.1) to FML 11 (26.x). */
    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    /** From the mod list, which exists before any mod's constructor runs: FML 4 to FML 11. */
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /** NeoForge's own brewing recipes, beside the vanilla mixes: NeoForgeBrewing. */
    @Override
    public void filterLoaderBrewing(PotionBrewing brewing, BlacklistSnapshot snapshot,
            Recorder recorder) {
        NeoForgeBrewing.filter(brewing, snapshot, recorder);
    }

    /**
     * Sends through PacketDistributor when the player's connection negotiated the channel:
     * NeoForge throws for a player without it, which a vanilla client and a GameTest player
     * are. The Type overload of hasChannel names no id class, so one call serves every release.
     */
    @Override
    public boolean sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(payload.type())) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, payload);
        return true;
    }
}
