package dev.srryo.parallaxaxiom.kill;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ReflectionAccess {
    private static final Unsafe UNSAFE = loadUnsafe();

    private ReflectionAccess() {
    }

    public static Unsafe unsafe() {
        return UNSAFE;
    }

    public static Field findField(Class<?> type, String... names) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = cursor.getDeclaredField(name);
                    try {
                        field.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        return null;
    }

    public static Object get(Object owner, String... names) {
        if (owner == null) {
            return null;
        }
        Field field = findField(owner.getClass(), names);
        return field == null ? null : read(field, owner);
    }

    public static Object getStatic(Class<?> owner, String... names) {
        Field field = findField(owner, names);
        return field == null ? null : read(field, null);
    }

    public static void put(Object owner, Object value, String... names) {
        if (owner == null) {
            return;
        }
        Field field = findField(owner.getClass(), names);
        if (field != null) {
            write(field, owner, value);
        }
    }

    public static void putStatic(Class<?> owner, Object value, String... names) {
        Field field = findField(owner, names);
        if (field != null) {
            write(field, null, value);
        }
    }

    public static Object invokeNoArgs(Object owner, String name) throws Exception {
        Method method = findMethod(owner.getClass(), name);
        if (method == null) {
            throw new NoSuchMethodException(owner.getClass().getName() + "." + name);
        }
        method.setAccessible(true);
        return method.invoke(owner);
    }

    public static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                return cursor.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Object read(Field field, Object owner) {
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        Object base = isStatic ? UNSAFE.staticFieldBase(field) : owner;
        long offset = isStatic ? UNSAFE.staticFieldOffset(field) : UNSAFE.objectFieldOffset(field);
        Class<?> type = field.getType();
        if (type == boolean.class) return UNSAFE.getBooleanVolatile(base, offset);
        if (type == int.class) return UNSAFE.getIntVolatile(base, offset);
        if (type == long.class) return UNSAFE.getLongVolatile(base, offset);
        if (type == float.class) return UNSAFE.getFloatVolatile(base, offset);
        if (type == double.class) return UNSAFE.getDoubleVolatile(base, offset);
        return UNSAFE.getObjectVolatile(base, offset);
    }

    private static void write(Field field, Object owner, Object value) {
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        Object base = isStatic ? UNSAFE.staticFieldBase(field) : owner;
        long offset = isStatic ? UNSAFE.staticFieldOffset(field) : UNSAFE.objectFieldOffset(field);
        Class<?> type = field.getType();
        if (type == boolean.class) UNSAFE.putBooleanVolatile(base, offset, Boolean.TRUE.equals(value));
        else if (type == int.class) UNSAFE.putIntVolatile(base, offset, ((Number) value).intValue());
        else if (type == long.class) UNSAFE.putLongVolatile(base, offset, ((Number) value).longValue());
        else if (type == float.class) UNSAFE.putFloatVolatile(base, offset, ((Number) value).floatValue());
        else if (type == double.class) UNSAFE.putDoubleVolatile(base, offset, ((Number) value).doubleValue());
        else UNSAFE.putObjectVolatile(base, offset, value);
    }

    private static Unsafe loadUnsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
