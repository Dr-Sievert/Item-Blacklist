package net.sievert.item_blacklist.line;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The release arithmetic backends, capabilities and gated mixins are picked by, with no game
 * running: releases compare part by part, a pre-release or snapshot counting as its release, a
 * window holds its since release but not its until, and a window folder's name is its window.
 */
class BackendsTest {
    @Test
    void comparesReleasesPartByPart() {
        assertTrue(Releases.compare("1.21.10", "1.21.9") > 0, "1.21.10 is above 1.21.9");
        assertTrue(Releases.compare("26.1.2", "26.2") < 0, "26.1.2 is below 26.2");
        assertTrue(Releases.compare("1.21.11", "26.1") < 0, "1.21.11 is below 26.1");
        assertEquals(0, Releases.compare("26.2", "26.2.0"), "a missing part counts as 0");
        assertEquals(0, Releases.compare("26.2-pre-1", "26.2"), "26.2-pre-1 counts as 26.2");
        assertEquals(0, Releases.compare("26.2-rc-1", "26.2"), "26.2-rc-1 counts as 26.2");
        assertEquals(0, Releases.compare("26.2-snapshot-1", "26.2"),
                "26.2-snapshot-1 counts as 26.2");
        assertEquals(0, Releases.compare("1.21.5-pre1", "1.21.5"), "1.21.5-pre1 counts as 1.21.5");
        assertEquals(0, Releases.compare("1.21.5-rc1", "1.21.5"), "1.21.5-rc1 counts as 1.21.5");
        assertTrue(Backends.since("1.21.5", "A").contains("1.21.5-rc1"),
                "since 1.21.5 holds 1.21.5-rc1");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Releases.compare("25w14a", "1.21.5"));
        assertEquals("not a Minecraft release: 25w14a", e.getMessage());
    }

    @Test
    void sinceHoldsItsReleaseAndEveryLaterOne() {
        Backends.Window window = Backends.since("1.21.5", "Since1_21_5");
        assertFalse(window.contains("1.21.4"));
        assertTrue(window.contains("1.21.5"));
        assertTrue(window.contains("1.21.10"));
        assertTrue(window.contains("26.2"));
    }

    @Test
    void untilStopsBelowItsRelease() {
        Backends.Window window = Backends.until("1.21.11", "Until1_21_11");
        assertTrue(window.contains("1.21.1"));
        assertTrue(window.contains("1.21.10"));
        assertFalse(window.contains("1.21.11"));
    }

    @Test
    void betweenHoldsItsSinceUpToItsUntil() {
        Backends.Window window = Backends.between("1.21.5", "1.21.6", "Since1_21_5Until1_21_6");
        assertFalse(window.contains("1.21.4"));
        assertTrue(window.contains("1.21.5"));
        assertFalse(window.contains("1.21.6"));
    }

    @Test
    void namesItsWindow() {
        assertEquals("since 26.2", Backends.since("26.2", "A").toString());
        assertEquals("until 26.2", Backends.until("26.2", "A").toString());
        assertEquals("since 1.21.5 until 1.21.6",
                Backends.between("1.21.5", "1.21.6", "A").toString());
    }

    @Test
    void readsAWindowFolder() {
        assertTrue(Releases.inFolder("since1_21_5-until1_21_6", "1.21.5"));
        assertFalse(Releases.inFolder("since1_21_5-until1_21_6", "1.21.6"));
        assertThrows(IllegalArgumentException.class, () -> Releases.inFolder("", "1.21.5"));
    }
}
