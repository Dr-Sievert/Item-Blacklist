package net.sievert.item_blacklist.recipes;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.StackRules;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * Judges a recipe by its displays, 26.1.2 and 26.2: the twin of the 1.21.x class of this name
 * in src/since1_21_2, for the 26.x record shapes (a stack display holds an ItemStackTemplate;
 * the imbue and dye recipes wrap an ingredient slot). It reads no tag contents: a tag slot is
 * its key (TagSlotDisplay), so a tag the strip emptied is still seen, where placementInfo()
 * would only say "not placeable". Special recipes have no display and are never judged.
 */
public final class RecipeJudgeSince1_21_2 {
    private RecipeJudgeSince1_21_2() {
    }

    /** The judge RecipePass calls, over one snapshot. */
    public static RecipePass.Judge forSnapshot(BlacklistSnapshot snapshot) {
        return (holder, causes) -> judge(snapshot, holder.value(), causes);
    }

    /** Adds the causes that remove the recipe: a blacklisted plain result, a blacklisted slot. */
    public static void judge(BlacklistSnapshot snapshot, Recipe<?> recipe,
            Consumer<String> causes) {
        for (RecipeDisplay display : recipe.display()) {
            judgeResult(snapshot, display.result(), causes);
            for (SlotDisplay slot : ingredientSlots(display)) {
                List<String> slotCauses = new ArrayList<>();
                if (fullyBlacklisted(snapshot, slot, slotCauses)) {
                    slotCauses.forEach(causes);
                }
            }
        }
    }

    /**
     * Only a plain result is judged. A demo or wrapped result (the smithing trim's chestplate,
     * the imbue recipe's WithAnyPotion, the dye recipe's DyedSlotDemo) is never: it shows an
     * example, so one blacklisted potion does not remove the tipped arrow recipe by its result.
     */
    static void judgeResult(BlacklistSnapshot snapshot, SlotDisplay result,
            Consumer<String> causes) {
        if (result instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
            ItemStack stack = stackDisplay.stack().create();
            if (!stack.isEmpty() && StackRules.blacklisted(snapshot, stack)) {
                causes.accept(RecipeRules.itemName(stack.getItem()));
            }
        } else if (result instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            Item item = itemDisplay.item().value();
            if (snapshot.item(item)) {
                causes.accept(RecipeRules.itemName(item));
            }
        }
    }

    /** The ingredient slots of the five vanilla display types; none for another (a mod's). */
    static List<SlotDisplay> ingredientSlots(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            return shaped.ingredients();
        }
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            return shapeless.ingredients();
        }
        if (display instanceof FurnaceRecipeDisplay furnace) {
            return List.of(furnace.ingredient());
        }
        if (display instanceof StonecutterRecipeDisplay stonecutter) {
            return List.of(stonecutter.input());
        }
        if (display instanceof SmithingRecipeDisplay smithing) {
            return List.of(smithing.template(), smithing.base(), smithing.addition());
        }
        return List.of();
    }

    /**
     * Whether every alternative of the slot is blacklisted; when it returns true, the causes are
     * added to {@code causes}. A tag slot counts when the tag is named or was emptied by the last
     * strip (itemTagEmptied); an empty slot or an unknown display never counts.
     */
    static boolean fullyBlacklisted(BlacklistSnapshot snapshot, SlotDisplay slot,
            List<String> causes) {
        if (slot instanceof SlotDisplay.TagSlotDisplay tagDisplay) {
            if (snapshot.itemTagEmptied(tagDisplay.tag())) {
                causes.add(RecipeRules.tagCause(tagDisplay.tag()));
                return true;
            }
            return false;
        }
        if (slot instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            Item item = itemDisplay.item().value();
            if (snapshot.item(item)) {
                causes.add(RecipeRules.itemName(item));
                return true;
            }
            return false;
        }
        if (slot instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
            ItemStack stack = stackDisplay.stack().create();
            if (!stack.isEmpty() && StackRules.blacklisted(snapshot, stack)) {
                causes.add(RecipeRules.itemName(stack.getItem()));
                return true;
            }
            return false;
        }
        if (slot instanceof SlotDisplay.WithRemainder remainder) {
            // An item with a crafting remainder: the input is what the slot takes.
            return fullyBlacklisted(snapshot, remainder.input(), causes);
        }
        if (slot instanceof SlotDisplay.Composite composite) {
            if (composite.contents().isEmpty()) {
                return false;
            }
            List<String> inner = new ArrayList<>();
            for (SlotDisplay part : composite.contents()) {
                if (!fullyBlacklisted(snapshot, part, inner)) {
                    return false;
                }
            }
            causes.addAll(inner);
            return true;
        }
        if (slot instanceof SlotDisplay.WithAnyPotion anyPotion) {
            // The imbue recipe's source slot: any potion of the wrapped item.
            return fullyBlacklisted(snapshot, anyPotion.display(), causes);
        }
        if (slot instanceof SlotDisplay.OnlyWithComponent withComponent) {
            // The dye recipe's dye slot: the wrapped items, restricted to one component.
            return fullyBlacklisted(snapshot, withComponent.source(), causes);
        }
        return false;
    }
}
