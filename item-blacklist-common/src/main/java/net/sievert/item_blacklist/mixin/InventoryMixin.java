package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.itemuse.ItemUse;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Holding and receiving: a blacklisted stack that enters a survival player's inventory is
 * deleted (add, which pickups, commands and most machines go through), and one that got there
 * another way is deleted at the next player tick, cursor included (tick). Server players only,
 * so the client's own player and the integrated server's client side are left alone; creative
 * players keep everything. The tick hook does not cancel: vanilla then skips the emptied stacks.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    /** Inventory.player: the owner, whose type and game mode decide whether a hook acts. */
    @Shadow
    @Final
    public Player player;

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"),
            cancellable = true)
    private void item_blacklist$deleteOnAdd(int slot, ItemStack stack,
            CallbackInfoReturnable<Boolean> cir) {
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlacklistSnapshot snapshot = Blacklist.server();
        if (snapshot.isEmpty() || ItemUse.creative(serverPlayer)) {
            return;
        }
        if (ItemUse.remove(snapshot, serverPlayer, stack, "inventory add")) {
            // The old mod's answer: "taken", so a pickup discards the emptied item entity.
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void item_blacklist$purgeOnTick(CallbackInfo ci) {
        if (!(this.player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BlacklistSnapshot snapshot = Blacklist.server();
        if (snapshot.isEmpty() || ItemUse.creative(serverPlayer)) {
            return;
        }
        ItemUse.purge(snapshot, serverPlayer);
    }
}
