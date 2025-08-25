package net.sievert.loot_blacklist.mixin;

import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.sievert.loot_blacklist.LootBlacklist;
import net.sievert.loot_blacklist.LootBlacklistConfig;
import static net.sievert.loot_blacklist.LootBlacklistLogger.*;
import static net.sievert.loot_blacklist.LootBlacklistLogger.Group.*;
import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ReloadableRegistries.class)
public class ReloadableRegistriesMixin {

    /**
     * Patches loot table entries using the *already validated* blacklist.
     * Assumes BlacklistValidator.validateAll() has been called before this mixin runs!
     */
    @Inject(
            method = "apply(Lnet/minecraft/registry/CombinedDynamicRegistries;Ljava/util/List;)Lnet/minecraft/registry/CombinedDynamicRegistries;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/registry/ReloadableRegistries;with(Lnet/minecraft/registry/CombinedDynamicRegistries;Ljava/util/List;)Lnet/minecraft/registry/CombinedDynamicRegistries;"
            )
    )
    private static void loot_blacklist$beforeFreeze(
            CombinedDynamicRegistries dynamicRegistries,
            List<MutableRegistry<?>> registries,
            CallbackInfoReturnable<CombinedDynamicRegistries<ServerDynamicRegistryType>> cir) {

        LootBlacklistConfig config = LootBlacklist.CONFIG;
        if (config == null) return;

        for (MutableRegistry<?> registry : registries) {
            RegistryKey<?> key = registry.getKey();
            if (key.getValue().toString().equals("minecraft:loot_table")) {
                @SuppressWarnings("unchecked")
                MutableRegistry<LootTable> lootRegistry = (MutableRegistry<LootTable>) registry;

                int totalModifyCount = 0;

                for (Identifier tableId : lootRegistry.getIds()) {
                    LootTable table = lootRegistry.get(tableId);
                    if (table == null) continue;

                    List<LootPool> originalPools = ((LootTableAccessor) table).getPools();
                    List<LootPool> rebuiltPools = new ArrayList<>();

                    int tableModifyCount = 0;

                    for (LootPool pool : originalPools) {
                        LootPool.Builder rebuilt = LootPool.builder()
                                .rolls(pool.rolls)
                                .bonusRolls(pool.bonusRolls);

                        pool.conditions.forEach(rebuilt::conditionally);
                        pool.functions.forEach(rebuilt::apply);

                        for (LootPoolEntry entry : ((LootPoolAccessor) pool).getEntries()) {
                            LootPoolEntry patched = patchEntry(entry, config);
                            if (patched != null) {
                                tableModifyCount++;
                                rebuilt.with(patched);
                            } else {
                                rebuilt.with(entry);
                            }
                        }
                        rebuiltPools.add(rebuilt.build());
                    }

                    if (tableModifyCount > 0) {
                        ((LootTableAccessor) table).setPools(rebuiltPools);
                        info(LOOT, "Patched " + tableModifyCount + " " +
                                pluralize(tableModifyCount, "entry", "entries") +
                                " in " + tableId);
                        totalModifyCount += tableModifyCount;
                    }
                }

                if (totalModifyCount > 0) {
                    info(LOOT, "Patched " + totalModifyCount + " total " +
                            pluralize(totalModifyCount, "loot table entry", "loot table entries"));
                }
            }
        }
    }

    @Unique
    private static LootPoolEntry patchEntry(LootPoolEntry entry, LootBlacklistConfig config) {
        if (entry instanceof ItemEntry itemEntry) {
            Identifier id = getEntryId(itemEntry);
            if (id != null && config.blacklist.contains(id)) {
                return EmptyEntry.builder().build();
            }
            return null;
        }

        if (entry instanceof CombinedEntry) {
            boolean patched = false;
            List<LootPoolEntry> newChildren = new ArrayList<>();
            for (LootPoolEntry child : ((CombinedEntryAccessor) entry).getChildren()) {
                LootPoolEntry patchedChild = patchEntry(child, config);
                if (patchedChild != null) {
                    patched = true;
                    newChildren.add(patchedChild);
                } else {
                    newChildren.add(child);
                }
            }
            if (patched && entry instanceof AlternativeEntry) {
                return AlternativeEntryInvoker.invokeInit(
                        newChildren,
                        ((LootPoolEntryAccessor) entry).getConditions()
                );
            }
            return patched ? entry : null;
        }

        return null;
    }

    @Unique
    private static Identifier getEntryId(LootPoolEntry entry) {
        if (entry instanceof ItemEntry itemEntry) {
            RegistryEntry<Item> regEntry = ((ItemEntryAccessor) itemEntry).getItemEntry();
            if (regEntry != null) {
                return Registries.ITEM.getId(regEntry.value());
            }
        }
        return null;
    }
}
