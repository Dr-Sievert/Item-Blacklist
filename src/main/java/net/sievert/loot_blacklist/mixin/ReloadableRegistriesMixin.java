package net.sievert.loot_blacklist.mixin;

import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.EmptyEntry;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.registry.CombinedDynamicRegistries;
import net.minecraft.registry.MutableRegistry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.ServerDynamicRegistryType;
import net.minecraft.util.Identifier;
import net.sievert.loot_blacklist.LootBlacklist;
import net.sievert.loot_blacklist.LootBlacklistConfig;
import net.minecraft.registry.Registries;
import net.minecraft.item.Item;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(targets = "net.minecraft.registry.ReloadableRegistries")
public class ReloadableRegistriesMixin {

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
        if (config == null) return; // not loaded yet

        int modifyCount = 0;

        // Find the loot table registry in the current reload context
        for (MutableRegistry<?> registry : registries) {
            RegistryKey<?> key = registry.getKey();
            if (key.getValue().toString().equals("minecraft:loot_table")) {
                @SuppressWarnings("unchecked")
                MutableRegistry<LootTable> lootRegistry = (MutableRegistry<LootTable>) registry;

                for (Identifier tableId : lootRegistry.getIds()) {
                    LootTable table = lootRegistry.get(tableId);
                    if (table == null) continue;

                    // Rebuild pools with blacklisted entries replaced by EmptyEntry
                    List<LootPool> originalPools = ((LootTableAccessor) table).getPools();
                    List<LootPool> rebuiltPools = new ArrayList<>();

                    for (LootPool pool : originalPools) {
                        LootPool.Builder rebuilt = LootPool.builder()
                                .rolls(pool.rolls)
                                .bonusRolls(pool.bonusRolls);

                        pool.conditions.forEach(rebuilt::conditionally);
                        pool.functions.forEach(rebuilt::apply);

                        for (LootPoolEntry entry : ((LootPoolAccessor) pool).getEntries()) {
                            Identifier id = getEntryId(entry);
                            if (id != null && config.blacklist.contains(id)) {
                                rebuilt.with(EmptyEntry.builder());
                                modifyCount++;
                                LootBlacklist.LOGGER.info("[loot_blacklist] {}: blacklisted, replaced {} with empty", tableId, id);
                            } else {
                                rebuilt.with(entry);
                            }
                        }
                        rebuiltPools.add(rebuilt.build());
                    }

                    // Overwrite pools if changes were made (or always, if you prefer strictness)
                    if (modifyCount > 0) {
                        ((LootTableAccessor) table).setPools(rebuiltPools);
                    }
                }
                if (modifyCount > 0) {
                    LootBlacklist.LOGGER.info("[loot_blacklist] Patched {} loot table entries in reload", modifyCount);
                }
            }
        }
    }

    // Helper copied from your LootBlacklist class (static for inner mixin)
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
