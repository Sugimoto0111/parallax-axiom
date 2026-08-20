package dev.srryo.parallaxaxiom.kill;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;

/**
 * Never referenced as a named class. TrustedKernel reads these class bytes as a resource
 * and defines a hidden copy, keeping Pig2's launch-time transformer away from this code.
 */
final class TrustedKernelTemplate {
    private static final Unsafe UNSAFE = unsafe();

    private TrustedKernelTemplate() {
    }

    static boolean executePig(Object rawTarget, Object ignoredLevel) {
        if (!(rawTarget instanceof Entity target)) {
            return false;
        }
        Class<?> pig2Class = target.getClass();
        try {
            write(findField(pig2Class, "gOshimai"), null, true);
            write(findField(pig2Class, "mIsJisatsu"), target, true);

            Object alive = read(findField(pig2Class, "gAliveServerPigs"), null);
            if (alive instanceof Collection<?> collection) {
                collection.remove(target);
            }
            Object deadIds = read(findField(pig2Class, "gJisatsuPigIDs"), null);
            if (deadIds instanceof Collection collection) {
                collection.add(target.getId());
            }

            Field removal = findField(pig2Class, "removalReason", "f_146795_");
            write(removal, target, Entity.RemovalReason.KILLED);

            Object callback = read(findField(pig2Class, "levelCallback", "f_146801_"), target);
            if (callback == null || callback == EntityInLevelCallback.NULL) {
                callback = read(findField(pig2Class, "mLevelCallbackBackup"), target);
            }
            if (callback instanceof EntityInLevelCallback entityCallback
                    && entityCallback != EntityInLevelCallback.NULL) {
                entityCallback.onRemove(Entity.RemovalReason.KILLED);
            }

            write(findField(pig2Class, "levelCallback", "f_146801_"), target,
                    EntityInLevelCallback.NULL);
            Field added = findField(pig2Class, "isAddedToWorld");
            if (added != null) write(added, target, false);
            Field canUpdate = findField(pig2Class, "canUpdate");
            if (canUpdate != null) write(canUpdate, target, false);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void resetPig(Object rawClass) {
        if (rawClass instanceof Class<?> pig2Class) {
            Field field = findField(pig2Class, "gOshimai");
            if (field != null) {
                write(field, null, false);
            }
        }
    }

    private static Field findField(Class<?> type, String... names) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (String name : names) {
                try {
                    return cursor.getDeclaredField(name);
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        return null;
    }

    private static Object read(Field field, Object owner) {
        if (field == null) return null;
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        Object base = isStatic ? UNSAFE.staticFieldBase(field) : owner;
        long offset = isStatic ? UNSAFE.staticFieldOffset(field) : UNSAFE.objectFieldOffset(field);
        if (field.getType() == boolean.class) return UNSAFE.getBooleanVolatile(base, offset);
        return UNSAFE.getObjectVolatile(base, offset);
    }

    private static void write(Field field, Object owner, Object value) {
        if (field == null) return;
        boolean isStatic = Modifier.isStatic(field.getModifiers());
        Object base = isStatic ? UNSAFE.staticFieldBase(field) : owner;
        long offset = isStatic ? UNSAFE.staticFieldOffset(field) : UNSAFE.objectFieldOffset(field);
        if (field.getType() == boolean.class) {
            UNSAFE.putBooleanVolatile(base, offset, Boolean.TRUE.equals(value));
        } else {
            UNSAFE.putObjectVolatile(base, offset, value);
        }
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (Unsafe) field.get(null);
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
