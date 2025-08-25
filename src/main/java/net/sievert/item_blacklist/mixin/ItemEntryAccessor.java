package net.sievert.item_blacklist.mixin;

import net.minecraft.item.Item;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accessor mixin for {@link ItemEntry}.
 * Exposes the private 'item' field so blacklist code
 * can inspect which Item a loot entry refers to.
 */
@Mixin(ItemEntry.class)
public interface ItemEntryAccessor {
    /**
     * Accessor for the 'item' field.
     * @return the registry entry of the Item this loot entry points to
     */
    @Accessor("item")
    RegistryEntry<Item> getItemEntry();
}
