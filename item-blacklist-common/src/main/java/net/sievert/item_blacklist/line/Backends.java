package net.sievert.item_blacklist.line;

import net.sievert.item_blacklist.ItemBlacklistMod;
import net.sievert.item_blacklist.platform.Services;
import java.util.ArrayList;
import java.util.List;

/**
 * Picks a backend for the running release, inside a line. One jar per line serves every
 * release of it; where a call changed inside that span, a facade lists one backend class per
 * window, each compiled by the module whose release it names and shipped in the line's jar.
 * At first use the facade asks this class, which reads the running release from the loader
 * (Platform#minecraftVersion), creates the one backend whose window contains it by class
 * name, and logs the choice. Backends never extend one another and nothing names them
 * directly, so a class compiled against 1.21.11 is never loaded on 1.21.1, and the reverse.
 * CLAUDE.md, "A name renamed or removed inside a line".
 */
public final class Backends {
    private Backends() {
    }

    /** A backend class and its window: since inclusive, until exclusive, null for open. */
    public record Window(String className, String since, String until) {
        boolean contains(String version) {
            return Releases.inWindow(version, since, until);
        }

        @Override
        public String toString() {
            if (since == null) {
                return "until " + until;
            }
            return until == null ? "since " + since : "since " + since + " until " + until;
        }
    }

    /** A backend for every release from {@code version} on. */
    public static Window since(String version, String className) {
        return new Window(className, version, null);
    }

    /** A backend for every release below {@code version}. */
    public static Window until(String version, String className) {
        return new Window(className, null, version);
    }

    /** A backend for the releases from {@code since} to below {@code until}. */
    public static Window between(String since, String until, String className) {
        return new Window(className, since, until);
    }

    /** The backend for the running release, created through its no-argument constructor. */
    public static <T> T pick(String what, Class<T> type, Window... windows) {
        Class<? extends T> chosen = choose(what, type, windows);
        try {
            return chosen.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(ItemBlacklistMod.NAME + " backend " + what
                    + ": cannot create " + chosen.getName(), e);
        }
    }

    /** The backend class for the running release, for backends created with arguments. */
    public static <T> Class<? extends T> choose(String what, Class<T> type, Window... windows) {
        String running = Services.PLATFORM.minecraftVersion();
        List<Window> matches = new ArrayList<>();
        for (Window window : windows) {
            if (window.contains(running)) {
                matches.add(window);
            }
        }
        if (matches.size() != 1) {
            throw new IllegalStateException(ItemBlacklistMod.NAME + " backend " + what + ": "
                    + matches.size() + " windows contain Minecraft " + running + " in "
                    + List.of(windows));
        }
        Window window = matches.get(0);
        try {
            Class<? extends T> chosen = Class.forName(
                    window.className(), true, Backends.class.getClassLoader()).asSubclass(type);
            ItemBlacklistMod.LOG.info(
                    ItemBlacklistMod.NAME + " backend {}: {} ({}) on Minecraft {}",
                    what, chosen.getSimpleName(), window, running);
            return chosen;
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(ItemBlacklistMod.NAME + " backend " + what + ": "
                    + window.className() + " is not in this jar", e);
        }
    }
}
