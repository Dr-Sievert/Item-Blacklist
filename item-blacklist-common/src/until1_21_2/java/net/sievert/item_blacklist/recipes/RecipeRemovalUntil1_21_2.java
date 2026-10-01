package net.sievert.item_blacklist.recipes;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.sievert.item_blacklist.line.RecipeIds;
import net.sievert.item_blacklist.report.Recorder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;

/**
 * The 1.21.1 removal pass, run after every registry's tags are bound, over the parsed recipes.
 * A recipe goes when it names a configured item tag, when its result is blacklisted, or when an
 * ingredient's alternatives are all blacklisted; an ingredient a stripped tag left without any
 * alternative counts through that tag. Window 1.21.1 only: it reads Ingredient.getItems(), the
 * list that 1.21.2 removed.
 */
public final class RecipeRemovalUntil1_21_2 {
    private RecipeRemovalUntil1_21_2() {
    }

    /**
     * The recipes that stay, or null when none goes; records every removal. Takes the tag ids
     * the manager's last apply read, so a second pass over the same load judges by result and
     * ingredients only.
     */
    public static List<RecipeHolder<?>> filter(RecipeManager manager, BlacklistSnapshot snapshot,
            Recorder recorder) {
        RecipeManagerStateUntil1_21_2 state = (RecipeManagerStateUntil1_21_2) manager;
        Map<String, Set<String>> tagNames = state.item_blacklist$takeTagNames();
        HolderLookup.Provider registries = state.item_blacklist$registries();
        return RecipePass.filter(manager.getRecipes(),
                (holder, causes) -> judge(snapshot, registries, holder.value(),
                        tagNames.getOrDefault(RecipeIds.name(holder), Set.of()), causes),
                recorder);
    }

    /**
     * Adds the causes that remove one recipe: each configured tag its JSON names; its result,
     * unless it is a trim recipe (whose result is a fixed demo chestplate); every alternative of
     * an ingredient whose alternatives are all blacklisted; and, for an ingredient left with no
     * alternative, each tag of its JSON that the last strip emptied.
     */
    static void judge(BlacklistSnapshot snapshot, HolderLookup.Provider registries,
            Recipe<?> recipe, Set<String> tagNames, Consumer<String> causes) {
        List<String> emptied = new ArrayList<>();
        for (String name : tagNames) {
            Optional<TagKey<Item>> tag = RecipeRules.itemTag(name);
            if (tag.isEmpty()) {
                continue;
            }
            if (snapshot.itemTag(tag.get())) {
                causes.accept(RecipeRules.tagCause(tag.get()));
            } else if (snapshot.itemTagEmptied(tag.get())) {
                emptied.add(RecipeRules.tagCause(tag.get()));
            }
        }
        if (!(recipe instanceof SmithingTrimRecipe)) {
            ItemStack result = recipe.getResultItem(registries);
            if (!result.isEmpty() && StackRules.blacklisted(snapshot, result)) {
                causes.accept(RecipeRules.itemName(result.getItem()));
            }
        }
        for (Ingredient ingredient : recipe.getIngredients()) {
            // An ingredient with no value at all is an empty slot of a shaped pattern, never a
            // tag ingredient.
            if (ingredient.isEmpty()) {
                continue;
            }
            List<ItemStack> alternatives = new ArrayList<>();
            for (ItemStack stack : ingredient.getItems()) {
                if (!placeholder(stack)) {
                    alternatives.add(stack);
                }
            }
            if (alternatives.isEmpty()) {
                // Left with nothing: a tag the blacklist emptied is the cause. A tag nothing
                // ever filled, in a recipe naming no emptied tag, keeps its recipe.
                emptied.forEach(causes);
                continue;
            }
            if (allBlacklisted(snapshot, alternatives)) {
                for (ItemStack stack : alternatives) {
                    causes.accept(RecipeRules.itemName(stack.getItem()));
                }
            }
        }
    }

    /**
     * NeoForge 1.21.1's stand-in for an empty tag: a barrier named "Empty Tag: <id>", which its
     * TagValue lists in place of no item. It is no real alternative.
     */
    static boolean placeholder(ItemStack stack) {
        return stack.is(Items.BARRIER) && stack.has(DataComponents.CUSTOM_NAME);
    }

    private static boolean allBlacklisted(BlacklistSnapshot snapshot, List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (!StackRules.blacklisted(snapshot, stack)) {
                return false;
            }
        }
        return true;
    }
}
