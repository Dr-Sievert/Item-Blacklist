package net.sievert.item_blacklist.integration.jer;

import net.minecraft.world.item.ItemStack;
import net.sievert.item_blacklist.blacklist.BlacklistManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class JerMixinUtil {

    private JerMixinUtil() {}

    public static List<ItemStack> filterStacks(
            List<ItemStack> stacks
    ) {
        if (stacks == null
                || stacks.isEmpty()) {
            return stacks;
        }

        List<ItemStack> filtered =
                new ArrayList<>(stacks.size());

        for (ItemStack stack : stacks) {
            if (stack == null
                    || !BlacklistManager.isBlacklisted(
                    stack
            )) {
                filtered.add(
                        stack
                );
            }
        }

        if (filtered.size() == stacks.size()) {
            return stacks;
        }

        return filtered;
    }

    public static boolean hasVisibleLootDrop(
            Object lootDrop
    ) {
        if (lootDrop == null) {
            return false;
        }

        try {
            Method getDrops =
                    lootDrop.getClass().getMethod(
                            "getDrops"
                    );

            Object result =
                    getDrops.invoke(
                            lootDrop
                    );

            if (!(result instanceof Collection<?> drops)) {
                return true;
            }

            if (drops.isEmpty()) {
                return false;
            }

            boolean foundItemStack = false;

            for (Object drop : drops) {
                if (!(drop instanceof ItemStack stack)) {
                    continue;
                }

                foundItemStack = true;

                if (!BlacklistManager.isBlacklisted(
                        stack
                )) {
                    return true;
                }
            }

            return !foundItemStack;
        } catch (ReflectiveOperationException exception) {
            return true;
        }
    }
}