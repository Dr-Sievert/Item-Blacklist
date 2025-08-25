package net.sievert.loot_blacklist.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.sievert.loot_blacklist.LootBlacklist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(
            method = "getTooltip(Lnet/minecraft/item/Item$TooltipContext;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/tooltip/TooltipType;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void loot_blacklist$overrideTooltip(
            Item.TooltipContext context,
            PlayerEntity player,
            TooltipType type,
            CallbackInfoReturnable<List<Text>> cir
    ) {
        ItemStack stack = (ItemStack)(Object)this;

        if (LootBlacklist.CONFIG == null) return;

        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (LootBlacklist.CONFIG.blacklist.contains(id)) {
            List<Text> original = cir.getReturnValue();
            // Always keep the first line (item name)
            Text name = original.isEmpty() ? stack.getName() : original.getFirst();
            // Replace all other tooltip lines with your warning
            Text warning = Text.translatable("item.loot_blacklist.disabled").formatted(Formatting.RED);
            cir.setReturnValue(List.of(name, warning));
        }
    }
}
