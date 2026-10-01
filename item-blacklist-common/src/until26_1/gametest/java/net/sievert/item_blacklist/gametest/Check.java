package net.sievert.item_blacklist.gametest;

import net.sievert.item_blacklist.line.Backends;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTestHelper's checks with a message, on the 1.21.x line. The message is a String up to
 * 1.21.4 and a {@code Component} from 1.21.5 (1.21.11 adds the String forms back), so a
 * scenario compiled at the line's floor that passed a String would fail with
 * NoSuchMethodError on 1.21.5 to 1.21.10. The scenarios call this facade; the calls go to
 * {@code CheckUntil1_21_5} or {@code CheckSince1_21_5}.
 */
public final class Check {
    /** The calls that differ; each backend implements them for its window. */
    public interface Backend {
        void fail(GameTestHelper helper, String message);

        void isTrue(GameTestHelper helper, boolean condition, String message);
    }

    private static final Backend BACKEND = Backends.pick("Check", Backend.class,
            Backends.until("1.21.5", "net.sievert.item_blacklist.gametest.CheckUntil1_21_5"),
            Backends.since("1.21.5", "net.sievert.item_blacklist.gametest.CheckSince1_21_5"));

    private Check() {
    }

    /** Fails the scenario with the message, in the form this release takes. */
    public static void fail(GameTestHelper helper, String message) {
        BACKEND.fail(helper, message);
    }

    /** Fails the scenario with the message unless the condition holds. */
    public static void isTrue(GameTestHelper helper, boolean condition, String message) {
        BACKEND.isTrue(helper, condition, message);
    }

    /**
     * Fails unless {@code actual} equals {@code expected}; {@code name} says what was compared.
     * The message is built here: vanilla's assertValueEqual swaps the two values in its message
     * from 1.21.5 until 26.2.
     */
    public static <N> void equal(GameTestHelper helper, N actual, N expected, String name) {
        if (!Objects.equals(actual, expected)) {
            fail(helper, "Expected " + name + " to be " + expected + ", was " + actual);
        }
    }
}
