package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.sievert.item_blacklist.itemuse.ItemUse;
import net.sievert.item_blacklist.line.Interactions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A right click on a blacklisted block does nothing for survival and adventure players: no
 * block use, no item use on it, no placing against it; the answer is "consumed", so the client
 * does not try the item on its own. Creative and spectator players are exempt. The held item is
 * not judged here: the inventory tick deletes a blacklisted one. The result comes from the
 * Interactions facade and nothing of InteractionResult is called, since that type changes from
 * an enum to a sealed interface at 1.21.2.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Inject(method = "useItemOn(Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)"
            + "Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void item_blacklist$refuseBlacklistedBlock(ServerPlayer player, Level level,
            ItemStack stack, InteractionHand hand, BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        BlacklistSnapshot snapshot = Blacklist.server();
        if (snapshot.isEmpty() || ItemUse.exemptFromBlocks(player)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!StackRules.blockBlacklisted(snapshot, level, pos, state)) {
            return;
        }
        ItemUse.refuseBlock(player, state, pos);
        cir.setReturnValue(Interactions.consume());
    }
}
