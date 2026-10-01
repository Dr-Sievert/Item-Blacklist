package net.sievert.item_blacklist.integration.jer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every path of JerReflect, with no game running: what a JER mixin gets for a member that is
 * there, missing, throwing, of the wrong type or a record's. The failing paths must give
 * FAILED or false and never throw, since a JER build that renamed a member must leave its page
 * unfiltered, not crash the client. They log the mod's WARN once per member.
 */
class JerReflectTest {
    /** A superclass with a private method, found by the declared-method walk. */
    static class Base {
        private String secret() {
            return "base";
        }
    }

    /** Stands in for a JER class: private final fields, a public and a throwing method. */
    static final class Sample extends Base {
        private final List<String> entries;
        private final int lastSet;
        private int set;

        Sample() {
            this.entries = new LinkedList<>(List.of("a"));
            // Set in the constructor: a constant initializer would be folded by javac.
            this.lastSet = 1;
            this.set = 2;
        }

        public String name() {
            return "sample";
        }

        public String boom() {
            throw new IllegalStateException("boom");
        }
    }

    /** A record: the JDK refuses writes to its final fields even when accessible. */
    record Pair(int value) {
    }

    @Test
    void callsAPublicMethod() {
        Sample sample = new Sample();
        assertEquals("sample", JerReflect.call(sample, "name"));
        assertEquals("sample", JerReflect.call(sample, "name"));
    }

    @Test
    void callsAPrivateMethodOfASuperclass() {
        assertEquals("base", JerReflect.call(new Sample(), "secret"));
    }

    @Test
    void missingMethodFails() {
        assertSame(JerReflect.FAILED, JerReflect.call(new Sample(), "nothing"));
    }

    @Test
    void throwingMethodFails() {
        assertSame(JerReflect.FAILED, JerReflect.call(new Sample(), "boom"));
    }

    @Test
    void nullTargetFails() {
        assertSame(JerReflect.FAILED, JerReflect.call(null, "name"));
        assertSame(JerReflect.FAILED, JerReflect.get(null, "set"));
        assertFalse(JerReflect.set(null, "set", 1));
    }

    @Test
    void readsAndWritesPrivateFinalFields() {
        Sample sample = new Sample();
        assertEquals(List.of("a"), JerReflect.get(sample, "entries"));
        assertTrue(JerReflect.set(sample, "entries", new ArrayList<>(List.of("b"))));
        assertEquals(List.of("b"), JerReflect.get(sample, "entries"));
        assertTrue(JerReflect.set(sample, "lastSet", 5));
        assertEquals(5, JerReflect.get(sample, "lastSet"));
        assertTrue(JerReflect.set(sample, "set", 0));
        assertEquals(0, JerReflect.get(sample, "set"));
    }

    @Test
    void refusesAWrongType() {
        Sample sample = new Sample();
        assertFalse(JerReflect.set(sample, "lastSet", "x"));
        assertEquals(1, JerReflect.get(sample, "lastSet"));
    }

    @Test
    void missingFieldFails() {
        Sample sample = new Sample();
        assertSame(JerReflect.FAILED, JerReflect.get(sample, "nothing"));
        assertFalse(JerReflect.set(sample, "nothing", 1));
    }

    @Test
    void recordFieldsStayFinal() {
        assertFalse(JerReflect.set(new Pair(1), "value", 2));
        assertEquals(1, JerReflect.get(new Pair(1), "value"));
    }

    @Test
    void failedIsNoCollectionAndNoNull() {
        assertNotNull(JerReflect.FAILED);
        assertFalse(JerReflect.FAILED instanceof Collection<?>);
        assertEquals("JerReflect.FAILED", JerReflect.FAILED.toString());
    }
}
