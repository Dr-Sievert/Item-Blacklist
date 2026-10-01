package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.loot.LootHooks;
import net.sievert.item_blacklist.loot.LootRecordBuffer;
import java.util.Optional;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * The loot JSON walk on 1.21.1: LootDataType.deserialize receives each table's raw tree with
 * the table's id, on the loot workers of a reload; from 1.21.2 loot parses through
 * SimpleJsonResourceReloadListener.scanDirectory instead (LootJsonMixinSince1_21_2Until1_21_4,
 * LootJsonMixinSince1_21_4). A wrap, not a HEAD inject, because the records of a table go to
 * the report only when deserialize returns it: a table NeoForge's conditions or a
 * LootTableLoadEvent listener drop comes back empty. Fabric's resource conditions drop a table
 * before this method is called. It names ResourceLocation, which its window, 1.21.1 only,
 * has.
 */
@Mixin(LootDataType.class)
public abstract class LootDataTypeMixinUntil1_21_2 {
    @WrapMethod(method = "deserialize(Lnet/minecraft/resources/ResourceLocation;"
            + "Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Ljava/util/Optional;")
    private Optional<?> item_blacklist$filterLootJson(ResourceLocation id, DynamicOps<?> ops,
            Object value, Operation<Optional<?>> original) {
        // Predicates and item modifiers parse through the same method: loot tables only.
        if ((Object) this != LootDataType.TABLE) {
            return original.call(id, ops, value);
        }
        LootRecordBuffer pending = LootHooks.beforeDecode(id.toString(), value);
        Optional<?> out = original.call(id, ops, value);
        // Present means loaded on both loaders; the EMPTY clause is a guard only.
        LootHooks.afterDecode(pending, out.isPresent() && out.get() != LootTable.EMPTY);
        return out;
    }
}
