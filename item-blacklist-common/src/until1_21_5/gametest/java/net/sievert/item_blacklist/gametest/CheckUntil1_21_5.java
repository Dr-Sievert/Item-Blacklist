package net.sievert.item_blacklist.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/** GameTest checks below 1.21.5: the message is a String. */
public final class CheckUntil1_21_5 implements Check.Backend {
    @Override
    public void fail(GameTestHelper helper, String message) {
        helper.fail(message);
    }

    @Override
    public void isTrue(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, message);
    }
}
