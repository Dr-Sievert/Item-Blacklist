package net.sievert.item_blacklist.blacklist;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Reads a data component of a stack without {@code ItemStack.get}: that call moved to another
 * interface at 1.21.5, so a call compiled at the 1.21.x floor fails on Fabric from 1.21.5. The
 * two members used here, has and getComponents, keep their declaring class and their Fabric
 * names on every release, so no backend is needed.
 */
public final class Components {
    private Components() {
    }

    /** The component's value, or null; the loop runs only for a stack that has the component. */
    @SuppressWarnings("unchecked")
    public static <T> T get(ItemStack stack, DataComponentType<T> type) {
        if (!stack.has(type)) {
            return null;
        }
        for (TypedDataComponent<?> component : stack.getComponents()) {
            // The identity test guards the cast: a component's value has its type's type.
            if (component.type() == type) {
                return (T) component.value();
            }
        }
        return null;
    }
}
