package net.sievert.item_blacklist.neoforge;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.blacklist.BlacklistBrewingManager;
import net.sievert.item_blacklist.blacklist.BlacklistManager;
import net.sievert.item_blacklist.integration.BlacklistJeiManager;
import net.sievert.item_blacklist.neoforge.brew.NeoForgeBrewingFilter;
import net.sievert.item_blacklist.network.BlacklistSyncPayload;

import java.util.List;

@Mod(ItemBlacklist.MOD_ID)
public final class ItemBlacklistNeoForge {

    private boolean brewingFiltered;

    public ItemBlacklistNeoForge(
            IEventBus modEventBus
    ) {
        ItemBlacklist.init(
                FMLPaths.CONFIGDIR.get()
        );

        modEventBus.addListener(
                this::registerPayloads
        );

        NeoForge.EVENT_BUS.addListener(
                this::onServerTick
        );

        NeoForge.EVENT_BUS.addListener(
                this::onPlayerLoggedIn
        );
    }

    private void registerPayloads(
            RegisterPayloadHandlersEvent event
    ) {
        PayloadRegistrar registrar =
                event.registrar("1");

        registrar.playToClient(
                BlacklistSyncPayload.TYPE,
                BlacklistSyncPayload.STREAM_CODEC,
                (payload, context) -> {
                    BlacklistManager.setSyncedBlacklist(
                            payload.items(),
                            payload.potions()
                    );

                    BlacklistBrewingManager.filter(
                            context.player()
                                    .level()
                                    .potionBrewing()
                    );

                    BlacklistJeiManager.filterBrewingRecipes();
                }
        );
    }

    private void onServerTick(
            ServerTickEvent.Pre event
    ) {
        if (this.brewingFiltered) {
            return;
        }

        this.brewingFiltered = true;

        NeoForgeBrewingFilter.filter(
                event.getServer().potionBrewing()
        );
    }

    private void onPlayerLoggedIn(
            PlayerEvent.PlayerLoggedInEvent event
    ) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        PacketDistributor.sendToPlayer(
                player,
                new BlacklistSyncPayload(
                        List.copyOf(
                                BlacklistManager.getResolvedBlacklist()
                        ),
                        List.copyOf(
                                BlacklistManager.getBlacklistedPotions()
                        )
                )
        );
    }
}