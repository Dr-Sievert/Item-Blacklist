package net.sievert.item_blacklist.mixin;

import net.sievert.item_blacklist.loot.LootHooks;
import net.sievert.item_blacklist.loot.LootJsonFilter;
import net.sievert.item_blacklist.loot.LootRecordBuffer;
import java.util.Map;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The loot JSON walk from 1.21.4: each data file's tree is the second argument of the one
 * Codec.parse in the scanDirectory overload that takes a FileToIdConverter, which the
 * other overloads (a registry key; NeoForge's optional values) call, and the loop's Map.Entry
 * holds the file id, read as text, so no id class is named. Only files under loot_table/ are
 * walked. Priority 900 puts this wrap inside Fabric API's resource-conditions wrap of the same
 * call (default 1000), so a table its fabric:load_conditions drop is never walked. The same
 * source serves the 26.x line as a twin of the same name in src/since26_1, under the one gate
 * since1_21_4: the method has the same shape on 26.1.2 and 26.2.
 */
@Mixin(value = SimpleJsonResourceReloadListener.class, priority = 900)
public abstract class LootJsonMixinSince1_21_4 {
    // The @At carries remap = false: its target is a DFU member no mapping names. The
    // method selector keeps the default and is remapped on Fabric.
    @WrapOperation(method = "scanDirectory(Lnet/minecraft/server/packs/resources/ResourceManager;"
            + "Lnet/minecraft/resources/FileToIdConverter;Lcom/mojang/serialization/DynamicOps;"
            + "Lcom/mojang/serialization/Codec;Ljava/util/Map;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;parse("
                    + "Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)"
                    + "Lcom/mojang/serialization/DataResult;", remap = false))
    private static DataResult<?> item_blacklist$filterLootJson(Codec<?> codec,
            DynamicOps<?> ops, Object input, Operation<DataResult<?>> original,
            @Local Map.Entry<?, ?> entry) {
        String tableId = LootJsonFilter.tableIdOfFile(String.valueOf(entry.getKey()));
        if (tableId == null) {
            return original.call(codec, ops, input);
        }
        LootRecordBuffer pending = LootHooks.beforeDecode(tableId, input);
        DataResult<?> result = original.call(codec, ops, input);
        LootHooks.afterDecode(pending, LootHooks.loaded(result));
        return result;
    }
}
