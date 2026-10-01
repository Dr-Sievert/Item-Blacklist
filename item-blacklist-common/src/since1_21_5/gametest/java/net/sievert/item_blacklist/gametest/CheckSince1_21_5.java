package net.sievert.item_blacklist.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/** GameTest checks from 1.21.5: the message is a Component. */
public final class CheckSince1_21_5 implements Check.Backend {
    @Override
    public void fail(GameTestHelper helper, String message) {
        helper.fail(Component.literal(message));
    }

    @Override
    public void isTrue(GameTestHelper helper, boolean condition, String message) {
        helper.assertTrue(condition, Component.literal(message));
    }
}
