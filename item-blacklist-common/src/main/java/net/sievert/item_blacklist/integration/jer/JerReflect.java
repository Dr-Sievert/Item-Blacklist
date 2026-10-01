package net.sievert.item_blacklist.integration.jer;

import net.sievert.item_blacklist.log.Log;
import net.sievert.item_blacklist.log.LogTag;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Every JER member a JER mixin needs, reached by name. The mixins compile against no JER jar
 * and hold no @Shadow, since a member one JER build lacks would fail the load of the one
 * required mixin config; here a member that is missing, not accessible, or throws gives FAILED
 * (or false), the entry stays unfiltered, and one WARN per class and member says so. Lookups
 * are cached per class and name. Names no class of JER or JEI.
 */
public final class JerReflect {
    /**
     * What call and get give when they cannot: never null, no Collection and no ItemStack, so
     * JerRules treats it as "could not be read" and keeps the entry.
     */
    public static final Object FAILED = new Object() {
        @Override
        public String toString() {
            return "JerReflect.FAILED";
        }
    };

    /** Per class: method name to the method found, or empty when none is. */
    private static final ClassValue<ConcurrentMap<String, Optional<Method>>> METHODS =
            new ClassValue<>() {
                @Override
                protected ConcurrentMap<String, Optional<Method>> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };
    /** Per class: field name to the field found, or empty when none is. */
    private static final ClassValue<ConcurrentMap<String, Optional<Field>>> FIELDS =
            new ClassValue<>() {
                @Override
                protected ConcurrentMap<String, Optional<Field>> computeValue(Class<?> type) {
                    return new ConcurrentHashMap<>();
                }
            };
    /** "class#member" of every member already warned about. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private JerReflect() {
    }

    /**
     * The result of the target's public or declared no-argument instance method of this name,
     * or FAILED when there is none, it cannot be called or it throws. Cached per class and name.
     */
    public static Object call(Object target, String method) {
        if (target == null) {
            return FAILED;
        }
        Class<?> type = target.getClass();
        Optional<Method> found =
                METHODS.get(type).computeIfAbsent(method, name -> findMethod(type, name));
        if (found.isEmpty()) {
            warn(type, method + "()", "no such method");
            return FAILED;
        }
        try {
            return found.get().invoke(target);
        } catch (InvocationTargetException e) {
            warn(type, method + "()", "threw " + e.getCause());
            return FAILED;
        } catch (IllegalAccessException | IllegalArgumentException e) {
            warn(type, method + "()", e.toString());
            return FAILED;
        }
    }

    /** The value of the target's instance field of this name, or FAILED when it cannot. */
    public static Object get(Object target, String field) {
        if (target == null) {
            return FAILED;
        }
        Class<?> type = target.getClass();
        Optional<Field> found =
                FIELDS.get(type).computeIfAbsent(field, name -> findField(type, name));
        if (found.isEmpty()) {
            warn(type, field, "no such field");
            return FAILED;
        }
        try {
            return found.get().get(target);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            warn(type, field, e.toString());
            return FAILED;
        }
    }

    /**
     * Writes the target's instance field of this name, final fields of an ordinary class
     * included; false when it cannot (no such field, a value of the wrong type, a record's
     * field, a refused access), and then the field keeps its value.
     */
    public static boolean set(Object target, String field, Object value) {
        if (target == null) {
            return false;
        }
        Class<?> type = target.getClass();
        Optional<Field> found =
                FIELDS.get(type).computeIfAbsent(field, name -> findField(type, name));
        if (found.isEmpty()) {
            warn(type, field, "no such field");
            return false;
        }
        try {
            found.get().set(target, value);
            return true;
        } catch (IllegalAccessException | IllegalArgumentException e) {
            warn(type, field, e.toString());
            return false;
        }
    }

    /**
     * The public method first (inherited ones included), then a declared instance method of the
     * class or a superclass. A public method of a class that is not public needs
     * trySetAccessible; a class whose signatures name an absent class throws a LinkageError,
     * which counts as "not found".
     */
    private static Optional<Method> findMethod(Class<?> type, String name) {
        try {
            Method method = type.getMethod(name);
            method.trySetAccessible();
            return Optional.of(method);
        } catch (NoSuchMethodException | LinkageError e) {
            // not public: look among the declared methods below
        }
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Method method = c.getDeclaredMethod(name);
                if (!Modifier.isStatic(method.getModifiers())) {
                    method.trySetAccessible();
                    return Optional.of(method);
                }
            } catch (NoSuchMethodException | LinkageError e) {
                // not declared here: try the superclass
            }
        }
        return Optional.empty();
    }

    /** A declared instance field of the class or a superclass, made accessible when it can be. */
    private static Optional<Field> findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                if (!Modifier.isStatic(field.getModifiers())) {
                    field.trySetAccessible();
                    return Optional.of(field);
                }
            } catch (NoSuchFieldException | LinkageError e) {
                // not declared here: try the superclass
            }
        }
        return Optional.empty();
    }

    /**
     * One WARN per class and member: a JER build that renamed or changed a member leaves its
     * page unfiltered, and a person reading the client log must be able to see which.
     */
    private static void warn(Class<?> type, String member, String cause) {
        if (WARNED.add(type.getName() + "#" + member)) {
            Log.warn(LogTag.RECIPE,
                    "JER member {}#{} cannot be used, its entries stay unfiltered: {}",
                    type.getName(), member, cause);
        }
    }
}
