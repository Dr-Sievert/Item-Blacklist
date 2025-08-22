package net.sievert.loot_blacklist.mixin;

import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.entry.AlternativeEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(AlternativeEntry.class)
public interface AlternativeEntryInvoker {
    @Invoker("<init>")
    static AlternativeEntry invokeInit(List<LootPoolEntry> children, List<LootCondition> conditions) {
        throw new AssertionError();
    }
}
