package dev.srryo.ultimatum.kill;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ObjectGraphPurger {
    private final Entity target;
    private final UUID uuid;
    private final Integer entityId;
    private final Set<Object> visited = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    ObjectGraphPurger(Entity target) {
        this.target = target;
        this.uuid = target.getUUID();
        this.entityId = target.getId();
    }

    void purge(Object root) {
        visit(root, 0);
    }

    private void visit(Object value, int depth) {
        if (value == null || depth > 8 || isLeaf(value) || !visited.add(value)) {
            return;
        }
        if (value.getClass().isArray()) {
            purgeArray(value, depth);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            purgeMap(map, depth);
            return;
        }
        if (value instanceof Collection<?> collection) {
            purgeCollection(collection, depth);
            return;
        }
        if (!shouldInspect(value.getClass())) {
            return;
        }
        for (Class<?> cursor = value.getClass(); cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                Object child = read(field, value);
                if (child == target) {
                    write(field, value, null);
                } else {
                    visit(child, depth + 1);
                }
            }
        }
    }

    private void purgeMap(Map<?, ?> map, int depth) {
        ArrayList<Map.Entry<?, ?>> snapshot;
        try {
            snapshot = new ArrayList<>(map.entrySet());
        } catch (Throwable ignored) {
            return;
        }
        for (Map.Entry<?, ?> entry : snapshot) {
            if (matches(entry.getKey()) || matches(entry.getValue())) {
                ContainerBypass.removeMapKey(map, entry.getKey());
            } else {
                visit(entry.getValue(), depth + 1);
            }
        }
    }

    private void purgeCollection(Collection<?> collection, int depth) {
        Object[] snapshot;
        try {
            snapshot = collection.toArray();
        } catch (Throwable ignored) {
            return;
        }
        for (Object child : snapshot) {
            if (matches(child)) {
                ContainerBypass.removeCollection(collection, child);
            } else {
                visit(child, depth + 1);
            }
        }
    }

    private void purgeArray(Object array, int depth) {
        if (array.getClass().getComponentType().isPrimitive()) {
            return;
        }
        for (int index = 0; index < Array.getLength(array); index++) {
            Object child = Array.get(array, index);
            if (matches(child)) {
                Array.set(array, index, null);
            } else {
                visit(child, depth + 1);
            }
        }
    }

    private boolean matches(Object value) {
        return value == target || uuid.equals(value) || entityId.equals(value);
    }

    private static boolean shouldInspect(Class<?> type) {
        String name = type.getName();
        return name.startsWith("net.minecraft.world.level.entity.")
                || name.startsWith("net.minecraft.server.level.")
                || name.startsWith("net.minecraft.util.ClassInstanceMultiMap")
                || name.startsWith("it.unimi.dsi.fastutil.")
                || name.startsWith("kakiku.pig2mod.map.")
                || name.startsWith("flashfur.omnimobs.")
                || name.startsWith("io.github.kosianodangoo.trialmonolith.");
    }

    private static boolean isLeaf(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Enum<?>
                || value instanceof UUID || value instanceof Class<?>;
    }

    private static Object read(Field field, Object owner) {
        try {
            long offset = ReflectionAccess.unsafe().objectFieldOffset(field);
            return ReflectionAccess.unsafe().getObject(owner, offset);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void write(Field field, Object owner, Object value) {
        try {
            long offset = ReflectionAccess.unsafe().objectFieldOffset(field);
            ReflectionAccess.unsafe().putObjectVolatile(owner, offset, value);
        } catch (Throwable ignored) {
        }
    }
}
