package net.sievert.item_blacklist.recipes;

import net.sievert.item_blacklist.line.RecipeIds;
import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import net.sievert.item_blacklist.report.Recorder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The removal pass both windows share: judge every recipe, record each removal with its
 * causes, keep the rest in order. A window-bound judge says why a recipe goes; this class
 * names only RecipeHolder as a type and its ids through RecipeIds, so it is one text on every
 * release.
 */
public final class RecipePass {
    private RecipePass() {
    }

    /** Reports the causes that remove one recipe; reporting none keeps it. */
    @FunctionalInterface
    public interface Judge {
        /** Hands every cause that removes {@code holder} to {@code causes}: an id or "#tag". */
        void judge(RecipeHolder<?> holder, Consumer<String> causes);
    }

    /**
     * Judges every holder in order and records (recipe id, cause) for each cause of each
     * removed one. A judge that throws a RuntimeException keeps its recipe and logs one WARN
     * naming it: a datapack's or another mod's recipe must not fail the reload. A LinkageError
     * is a fault of the port and is not caught, so the start reload fails loudly.
     *
     * @return the kept holders in the input's order, or null when nothing was removed
     */
    public static List<RecipeHolder<?>> filter(Collection<RecipeHolder<?>> holders, Judge judge,
            Recorder recorder) {
        List<RecipeHolder<?>> kept = new ArrayList<>(holders.size());
        boolean removed = false;
        for (RecipeHolder<?> holder : holders) {
            Set<String> causes = new TreeSet<>();
            try {
                judge.judge(holder, causes::add);
            } catch (RuntimeException e) {
                Log.warn(LogTag.RECIPE, "Could not judge recipe {}, it stays: {}",
                        RecipeIds.name(holder), e.toString());
                causes.clear();
            }
            if (causes.isEmpty()) {
                kept.add(holder);
                continue;
            }
            removed = true;
            String id = RecipeIds.name(holder);
            for (String cause : causes) {
                recorder.recipeRemoval(id, cause);
            }
        }
        return removed ? kept : null;
    }
}
