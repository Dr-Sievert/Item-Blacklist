package net.sievert.item_blacklist.mixin;

import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.*;
import net.minecraft.registry.*;
import net.minecraft.util.Identifier;
import net.sievert.item_blacklist.ItemBlacklist;
import net.sievert.item_blacklist.BlacklistConfig;

import static net.sievert.item_blacklist.BlacklistLogger.*;
import static net.sievert.item_blacklist.BlacklistLogger.Group.*;

import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin for {@link ReloadableRegistries}.
 * Filters loot tables during reload, replacing
 * blacklisted item entries with empty entries.
 */
@Mixin(ReloadableRegistries.class)
public class ReloadableRegistriesMixin {

    /**
     * Injects before loot table registries are frozen,
     * patching out blacklisted loot entries.
     */
    @Inject(
            method = "apply(Lnet/minecraft/registry/CombinedDynamicRegistries;Ljava/util/List;)Lnet/minecraft/registry/CombinedDynamicRegistries;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/registry/ReloadableRegistries;with(Lnet/minecraft/registry/CombinedDynamicRegistries;Ljava/util/List;)Lnet/minecraft/registry/CombinedDynamicRegistries;"
            )
    )
    private static void item_blacklist$beforeFreeze(
            CombinedDynamicRegistries dynamicRegistries,
            List<MutableRegistry<?>> registries,
            CallbackInfoReturnable<CombinedDynamicRegistries<ServerDynamicRegistryType>> cir) {

        BlacklistConfig config = ItemBlacklist.CONFIG;
        if (config == null) return;

        int totalTablesPatched = 0;
        int totalEntriesRemoved = 0;

        for (MutableRegistry<?> registry : registries) {
            RegistryKey<?> key = registry.getKey();
            if (key.getValue().toString().equals("minecraft:loot_table")) {
                @SuppressWarnings("unchecked")
                MutableRegistry<LootTable> lootRegistry = (MutableRegistry<LootTable>) registry;

                for (Identifier tableId : lootRegistry.getIds()) {
                    LootTable table = lootRegistry.get(tableId);
                    if (table == null) continue;

                    List<LootPool> originalPools = ((LootTableAccessor) table).getPools();
                    List<LootPool> rebuiltPools = new ArrayList<>();

                    int tableRemoved = 0;

                    for (LootPool pool : originalPools) {
                        LootPool.Builder rebuilt = LootPool.builder()
                                .rolls(pool.rolls)
                                .bonusRolls(pool.bonusRolls);

                        pool.conditions.forEach(rebuilt::conditionally);
                        pool.functions.forEach(rebuilt::apply);

                        for (LootPoolEntry entry : ((LootPoolAccessor) pool).getEntries()) {
                            LootPoolEntry patched = patchEntry(entry, config);
                            if (patched != null) {
                                tableRemoved++;
                                rebuilt.with(patched);
                            } else {
                                rebuilt.with(entry);
                            }
                        }
                        rebuiltPools.add(rebuilt.build());
                    }

                    if (tableRemoved > 0) {
                        ((LootTableAccessor) table).setPools(rebuiltPools);
                        totalTablesPatched++;
                        totalEntriesRemoved += tableRemoved;
                    }
                }
            }
        }

        if (totalEntriesRemoved > 0) {
            info(LOOT, "Loot blacklist: removed " +
                    totalEntriesRemoved + " " + pluralize(totalEntriesRemoved, "entry", "entries") +
                    " in " + totalTablesPatched + " " + pluralize(totalTablesPatched, "loot table", "loot tables"));
        } else {
            info(LOOT, "No blacklisted loot entries found.");
        }
    }

    /**
     * Recursively patches a loot pool entry, replacing
     * blacklisted item entries or rebuilding combined entries.
     */
    @Unique
    private static LootPoolEntry patchEntry(LootPoolEntry entry, BlacklistConfig config) {
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

    /**
     * Resolves the {@link Identifier} of an item loot entry.
     */
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
