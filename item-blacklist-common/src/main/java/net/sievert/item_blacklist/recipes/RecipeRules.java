package net.sievert.item_blacklist.recipes;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.id.IdText;
import net.sievert.item_blacklist.line.Keys;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The recipes subsystem's rules that name nothing window-bound: pure functions of a snapshot,
 * shared by the match-time hooks, both removal passes and the scenarios, so every window judges
 * and names a removal the same way.
 */
public final class RecipeRules {
    private RecipeRules() {
    }

    /**
     * Whether matching {@code stack} is refused by Ingredient.test and by the recipe picker: the
     * stack's item is blacklisted. Item-level on purpose: a potion holding a blacklisted potion
     * is brewing's business and an enchanted tool enchanting's, so a recipe that takes a potion
     * or a tool never refuses a clean one. The empty snapshot is asked first (a hook on a client
     * or a worker thread pays one read), and a null stack is refused nothing (1.21.1's test takes
     * a nullable stack).
     */
    public static boolean refusesMatch(BlacklistSnapshot snapshot, ItemStack stack) {
        return !snapshot.isEmpty() && stack != null && !stack.isEmpty()
                && snapshot.item(stack.getItem());
    }

    /**
     * "namespace:path" of a registered item: the cause of a removal by item. Only items a
     * snapshot holds are named here, and a snapshot holds only keyed items, so the throw is
     * unreachable; if it is reached, RecipePass keeps the recipe and warns.
     */
    public static String itemName(Item item) {
        String name = Lookups.itemName(item, null);
        if (name == null) {
            throw new IllegalStateException("an item without a registry key");
        }
        return name;
    }

    /** "#namespace:path": the cause of a removal by item tag, as the report writes tags. */
    public static String tagCause(TagKey<Item> tag) {
        return "#" + Keys.name(tag);
    }

    /**
     * The item tag a data-form text id names ("planks" is "minecraft:planks"); empty when the
     * text is no id. Recipe JSON writes ids in the data form, so a missing namespace is vanilla's.
     */
    public static Optional<TagKey<Item>> itemTag(String text) {
        return IdText.parseLenient(text)
                .map(id -> Keys.tag(Registries.ITEM, id.namespace(), id.path()));
    }
}
