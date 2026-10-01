package net.sievert.item_blacklist.neoforge;

import net.sievert.item_blacklist.blacklist.BlacklistSnapshot;
import net.sievert.item_blacklist.blacklist.Lookups;
import net.sievert.item_blacklist.blacklist.StackRules;
import net.sievert.item_blacklist.brewing.BrewingText;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.neoforge.line.NeoForgeIngredients;
import net.sievert.item_blacklist.report.Recorder;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.brewing.BrewingRecipeRegistry;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;

/**
 * NeoForge's own brewing recipes, which live in PotionBrewing.registry, a private final field
 * only NeoForge's patch has: filtered from the list first read per PotionBrewing object and
 * written back by reflection. An accessor mixin would need a second mixin config for one field,
 * and an access transformer is validated against vanilla, which lacks it. Every call looks the
 * field up and reads it, also with nothing to remove, so every NeoForge boot proves the access;
 * a failure is one ERROR per object and the recipes stay as they are. Called by
 * NeoForgePlatform.filterLoaderBrewing only, on the server's object at every starting and good
 * reload and on each client connection's object.
 */
public final class NeoForgeBrewing {
    /** The report table of these removals. */
    public static final String TABLE = "neoforge:brewing";

    /** The recipes as first read, per object; the object is the key, weakly. */
    private static final Map<PotionBrewing, List<IBrewingRecipe>> ORIGINALS =
            Collections.synchronizedMap(new WeakHashMap<>());
    /** Objects whose registry could not be reached: one ERROR each. */
    private static final Set<PotionBrewing> FAILED =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
    /** PotionBrewing.registry, looked up once. */
    private static volatile Field registry;

    private NeoForgeBrewing() {
    }

    /**
     * Probes the field, then replaces the registry of this PotionBrewing by its originals
     * without every BrewingRecipe the snapshot removes; each removal is recorded. Other
     * IBrewingRecipes are kept: their inputs cannot be read.
     */
    public static void filter(PotionBrewing brewing, BlacklistSnapshot snapshot,
            Recorder recorder) {
        Field field;
        try {
            field = registryField();
            Object current = field.get(brewing);
            if (!(current instanceof BrewingRecipeRegistry)) {
                throw new IllegalStateException("PotionBrewing.registry holds " + current);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            unreachable(brewing, e);
            return;
        }
        List<IBrewingRecipe> originals =
                ORIGINALS.computeIfAbsent(brewing, b -> List.copyOf(b.getRecipes()));
        List<IBrewingRecipe> kept = new ArrayList<>(originals.size());
        for (IBrewingRecipe recipe : originals) {
            if (recipe instanceof BrewingRecipe brewingRecipe && removed(snapshot, brewingRecipe)) {
                recorder.loaderRemoval(TABLE, describe(brewingRecipe));
            } else {
                kept.add(recipe);
            }
        }
        try {
            field.set(brewing, new BrewingRecipeRegistry(List.copyOf(kept)));
        } catch (ReflectiveOperationException | RuntimeException e) {
            unreachable(brewing, e);
        }
    }

    /**
     * True when the output is blacklisted by the stack rule, or every alternative of the input
     * or of the reagent is. Item-level only: a potion-level input check is not made, since the
     * input is an ingredient of items.
     */
    public static boolean removed(BlacklistSnapshot snapshot, BrewingRecipe recipe) {
        if (snapshot.isEmpty()) {
            return false;
        }
        return StackRules.blacklisted(snapshot, recipe.getOutput())
                || allBlacklisted(snapshot, recipe.getInput())
                || allBlacklisted(snapshot, recipe.getIngredient());
    }

    /** Whether the ingredient lists alternatives and every one is blacklisted. */
    private static boolean allBlacklisted(BlacklistSnapshot snapshot, Ingredient ingredient) {
        List<Item> items = NeoForgeIngredients.alternatives(ingredient);
        if (items.isEmpty()) {
            return false;
        }
        for (Item item : items) {
            if (!snapshot.item(item)) {
                return false;
            }
        }
        return true;
    }

    /**
     * "input=[ids], ingredient=[ids], output=id": the old NeoForge filter's form, the input
     * being a list of items here.
     */
    private static String describe(BrewingRecipe recipe) {
        return BrewingText.mix(
                Lookups.itemNames(NeoForgeIngredients.alternatives(recipe.getInput()),
                        BrewingText.DIRECT).toString(),
                Lookups.itemNames(NeoForgeIngredients.alternatives(recipe.getIngredient()),
                        BrewingText.DIRECT),
                Lookups.itemName(recipe.getOutput().getItem(), BrewingText.DIRECT));
    }

    /**
     * The field, looked up once. NeoForge runs on Mojang names on every release, so the name
     * is the source's. setAccessible may throw InaccessibleObjectException, a RuntimeException.
     */
    private static Field registryField() throws ReflectiveOperationException {
        Field field = registry;
        if (field == null) {
            field = PotionBrewing.class.getDeclaredField("registry");
            field.setAccessible(true);
            registry = field;
        }
        return field;
    }

    /**
     * One ERROR per object, that is per server and per client connection: a fault of the port
     * or of the environment. The recipes stay as they are.
     */
    private static void unreachable(PotionBrewing brewing, Exception cause) {
        if (FAILED.add(brewing)) {
            Log.error(LogTag.POTION, "Cannot reach NeoForge's brewing recipes: {}",
                    String.valueOf(cause));
        }
    }
}
