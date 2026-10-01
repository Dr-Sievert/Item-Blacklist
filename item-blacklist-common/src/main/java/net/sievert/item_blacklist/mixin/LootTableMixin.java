package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.blacklist.Blacklist;
import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.loot.LootRolls;
import java.util.function.Consumer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The roll filter, on every release and both loaders: every stack a loot roll hands out is
 * judged by the stack rule, whatever produced it. The wrap of getRandomItemsRaw covers every
 * roll, nested tables included, after the table's own functions; mixin priority 3100 puts it
 * outside Fabric API's wrapper of the same method (3000, from its 1.21.6 builds), so it also
 * sees what MODIFY_DROPS listeners add. The RETURN hook of the private
 * getRandomItems(LootContext) sees what NeoForge's global loot modifiers add after the roll.
 * Both read the running server's snapshot; rolls happen on the logical server only.
 */
@Mixin(value = LootTable.class, priority = 3100)
public abstract class LootTableMixin {
    @WrapMethod(method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;"
            + "Ljava/util/function/Consumer;)V")
    private void item_blacklist$filterRoll(LootContext context, Consumer<ItemStack> output,
            Operation<Void> original) {
        BlacklistSnapshot snapshot = Blacklist.server();
        Consumer<ItemStack> filtered =
                snapshot.isEmpty() ? output : LootRolls.filtering(snapshot, output);
        original.call(context, filtered);
    }

    @ModifyReturnValue(method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/"
            + "LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN"))
    private ObjectArrayList<ItemStack> item_blacklist$filterModifiedLoot(
            ObjectArrayList<ItemStack> loot) {
        BlacklistSnapshot snapshot = Blacklist.server();
        if (!snapshot.isEmpty()) {
            LootRolls.removeBlacklisted(snapshot, loot);
        }
        return loot;
    }
}
