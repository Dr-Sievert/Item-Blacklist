package net.sievert.item_blacklist.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.sievert.item_blacklist.ItemBlacklist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin for {@link ItemStack}.
 * Injects into tooltip building to display a warning
 * if the item is blacklisted.
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    /**
     * Injects after tooltip generation to override lines
     * with a blacklist warning if the item is disabled.
     */
    @Inject(
            method = "getTooltip(Lnet/minecraft/item/Item$TooltipContext;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/tooltip/TooltipType;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void item_blacklist$overrideTooltip(
            Item.TooltipContext context,
            PlayerEntity player,
            TooltipType type,
            CallbackInfoReturnable<List<Text>> cir
    ) {
        ItemStack stack = (ItemStack)(Object)this;

        if (ItemBlacklist.CONFIG == null) return;

        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (ItemBlacklist.CONFIG.blacklist.contains(id)) {
            List<Text> original = cir.getReturnValue();
            Text name = original.isEmpty() ? stack.getName() : original.getFirst();
            Text warning = Text.translatable("item.blacklist.disabled").formatted(Formatting.RED);
            cir.setReturnValue(List.of(name, warning));
        }
    }
}
