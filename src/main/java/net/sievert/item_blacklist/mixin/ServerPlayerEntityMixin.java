package net.sievert.item_blacklist.mixin;

import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.sievert.item_blacklist.ItemBlacklist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(
            method = "tick",
            at = @At("TAIL")
    )
    private void item_blacklist$purgeInventory(CallbackInfo ci) {
        ServerPlayerEntity player = (ServerPlayerEntity)(Object)this;
        if (ItemBlacklist.CONFIG == null) return;
        if (player.isCreative()) return;

        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                var id = Registries.ITEM.getId(stack.getItem());
                if (ItemBlacklist.CONFIG.blacklist.contains(id)) {
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                    player.sendMessage(
                            Text.translatable("item.blacklist.disabled")
                                    .formatted(Formatting.RED),
                            true
                    );
                }
            }
        }
    }
}