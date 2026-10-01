package net.sievert.item_blacklist.gametest;

import java.util.Objects;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * GameTestHelper's checks with a message, on this line: the String forms, which every
 * release of it has. 1.21.5 to 1.21.10 take a {@code Component} instead, and a String call
 * compiled elsewhere fails there with NoSuchMethodError, so scenarios pass their message
 * through this class, the one place that changes for a line that spans those releases.
 */
public final class Check {
    private Check() {
    }

    /** Fails the scenario with the message, in the form this release takes. */
    public static void fail(GameTestHelper helper, String message) {
        helper.fail(message);
    }

    /** Fails the scenario with the message unless the condition holds. */
    public static void isTrue(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
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
