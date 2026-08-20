package dev.srryo.parallaxaxiom.kill;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rebuilds the vanilla world indexes without the target and atomically replaces their
 * backing containers. Replacing a container is intentionally stronger than calling its
 * public remove method: hostile wrappers can reject remove while they cannot intercept an
 * Unsafe write to the owning field.
 *
 * <p>The EntityLookup map replacement strategy was independently implemented after studying
 * No-Sugar 1.9.1 (MIT, copyright consome-c11). Parallax Axiom additionally rewrites every loaded
 * section, the tick-list maps, known UUIDs and the ChunkMap tracking map before its existing
 * object-graph purge and tombstone guards run.</p>
 */
public final class DeterministicWorldIndexEraser {
    private DeterministicWorldIndexEraser() {
    }

    static void erase(ServerLevel level, Entity target) {
        rewriteTickList(ReflectionAccess.get(level, "entityTickList", "f_143243_"), target);

        Object manager = ReflectionAccess.get(level, "entityManager", "f_143244_");
        rewriteManager(manager, target);

        Object chunkSource = level.getChunkSource();
        Object chunkMap = ReflectionAccess.get(chunkSource, "chunkMap", "f_8325_", "f_140150_");
        rewriteIntMapField(chunkMap, target, "entityMap", "f_140150_");
    }

    /**
     * Stops all server-side AI/entity ticks while leaving section lookup and ChunkMap
     * tracking intact. The client can therefore render an already-established death
     * animation without the entity getting another opportunity to move or retaliate.
     */
    public static void suspendServerTicking(ServerLevel level, Entity target) {
        rewriteTickList(ReflectionAccess.get(level, "entityTickList", "f_143243_"), target);
    }

    /** Performs the same atomic index replacement against ClientLevel without linking
     * client-only classes into the dedicated-server class path. */
    public static void eraseClient(Object clientLevel, Entity target) {
        rewriteTickList(ReflectionAccess.get(clientLevel,
                "tickingEntities", "f_171630_"), target);

        Object manager = ReflectionAccess.get(clientLevel,
                "entityStorage", "f_171631_");
        if (manager == null) {
            return;
        }
        Object storage = ReflectionAccess.get(manager,
                "entityStorage", "f_157637_");
        rewriteLookup(storage, target);

        Object getter = ReflectionAccess.get(manager, "entityGetter", "f_157640_");
        Object getterVisible = ReflectionAccess.get(getter,
                "visibleEntities", "f_156940_");
        if (getterVisible != storage) {
            rewriteLookup(getterVisible, target);
        }

        Object sections = ReflectionAccess.get(manager,
                "sectionStorage", "f_157638_");
        rewriteAllSections(sections, target);

        ReflectionAccess.put(target, Entity.RemovalReason.KILLED,
                "removalReason", "f_146795_");
        ReflectionAccess.put(target, false, "isAddedToWorld");
        ReflectionAccess.put(target, false, "canUpdate");
    }

    private static void rewriteManager(Object manager, Entity target) {
        if (manager == null) {
            return;
        }

        Object visible = ReflectionAccess.get(manager,
                "visibleEntityStorage", "f_157494_");
        rewriteLookup(visible, target);

        Object getter = ReflectionAccess.get(manager, "entityGetter", "f_157496_");
        Object getterVisible = ReflectionAccess.get(getter,
                "visibleEntities", "f_156940_");
        if (getterVisible != visible) {
            rewriteLookup(getterVisible, target);
        }

        replaceKnownUuids(manager, target.getUUID());

        Object sectionStorage = ReflectionAccess.get(manager,
                "sectionStorage", "f_157495_");
        rewriteAllSections(sectionStorage, target);
    }

    @SuppressWarnings("unchecked")
    private static void rewriteLookup(Object lookup, Entity target) {
        if (lookup == null) {
            return;
        }

        Object rawIds = ReflectionAccess.get(lookup, "byId", "f_156807_");
        if (rawIds instanceof Int2ObjectMap<?> ids) {
            Int2ObjectLinkedOpenHashMap<Object> next = copyWithout(ids, target);
            ReflectionAccess.put(lookup, next, "byId", "f_156807_");
        }

        Object rawUuids = ReflectionAccess.get(lookup, "byUuid", "f_156808_");
        if (rawUuids instanceof Map<?, ?> uuids) {
            Map<Object, Object> next = new HashMap<>(Math.max(16, uuids.size()));
            for (Map.Entry<?, ?> entry : new ArrayList<>(uuids.entrySet())) {
                if (!matches(entry.getKey(), entry.getValue(), target)) {
                    next.put(entry.getKey(), entry.getValue());
                }
            }
            ReflectionAccess.put(lookup, next, "byUuid", "f_156808_");
        }
    }

    private static void rewriteTickList(Object tickList, Entity target) {
        if (tickList == null) {
            return;
        }

        String[][] fields = {
                {"active", "f_156903_"},
                {"passive", "f_156904_"},
                {"iterated", "f_156905_"}
        };
        IdentityHashMap<Object, Object> replacements = new IdentityHashMap<>();
        for (String[] names : fields) {
            Object current = ReflectionAccess.get(tickList, names);
            if (!(current instanceof Int2ObjectMap<?> map)) {
                continue;
            }
            Object next = replacements.computeIfAbsent(current, ignored -> copyWithout(map, target));
            ReflectionAccess.put(tickList, next, names);
        }
    }

    private static void rewriteIntMapField(Object owner, Entity target, String... names) {
        if (owner == null) {
            return;
        }
        Object rawMap = ReflectionAccess.get(owner, names);
        if (rawMap instanceof Int2ObjectMap<?> map) {
            ReflectionAccess.put(owner, copyWithout(map, target), names);
        }
    }

    private static Int2ObjectLinkedOpenHashMap<Object> copyWithout(Int2ObjectMap<?> source,
                                                                   Entity target) {
        Int2ObjectLinkedOpenHashMap<Object> next = new Int2ObjectLinkedOpenHashMap<>();
        for (Int2ObjectMap.Entry<?> entry : new ArrayList<>(source.int2ObjectEntrySet())) {
            if (!matches(entry.getIntKey(), entry.getValue(), target)) {
                next.put(entry.getIntKey(), entry.getValue());
            }
        }
        return next;
    }

    private static void replaceKnownUuids(Object manager, UUID targetUuid) {
        Object rawKnown = ReflectionAccess.get(manager, "knownUuids", "f_157491_");
        if (!(rawKnown instanceof Collection<?> known)) {
            return;
        }
        Set<Object> next = new HashSet<>(known);
        next.remove(targetUuid);
        ReflectionAccess.put(manager, next, "knownUuids", "f_157491_");
    }

    private static void rewriteAllSections(Object sectionStorage, Entity target) {
        if (sectionStorage == null) {
            return;
        }
        Object rawSections = ReflectionAccess.get(sectionStorage, "sections", "f_156852_");
        if (!(rawSections instanceof Map<?, ?> sections)) {
            return;
        }
        for (Object section : new ArrayList<>(sections.values())) {
            Object storage = ReflectionAccess.get(section, "storage", "f_156827_");
            if (storage instanceof ClassInstanceMultiMap<?> multiMap) {
                rewriteMultiMap(multiMap, target);
            }
        }
    }

    private static void rewriteMultiMap(ClassInstanceMultiMap<?> multiMap, Entity target) {
        Object rawAll = ReflectionAccess.get(multiMap, "allInstances", "f_13529_");
        if (!(rawAll instanceof Collection<?> all)) {
            return;
        }

        List<Object> nextAll = new ArrayList<>();
        for (Object value : new ArrayList<>(all)) {
            if (!matches(null, value, target)) {
                nextAll.add(value);
            }
        }

        Object baseClass = ReflectionAccess.get(multiMap, "baseClass", "f_13528_");
        Object rawByClass = ReflectionAccess.get(multiMap, "byClass", "f_13527_");
        Map<Object, List<Object>> nextByClass = new HashMap<>();
        if (rawByClass instanceof Map<?, ?> byClass) {
            for (Map.Entry<?, ?> entry : new ArrayList<>(byClass.entrySet())) {
                List<Object> nextValues;
                if (entry.getKey() == baseClass || entry.getKey().equals(baseClass)) {
                    nextValues = nextAll;
                } else {
                    nextValues = new ArrayList<>();
                    if (entry.getValue() instanceof Collection<?> values) {
                        for (Object value : new ArrayList<>(values)) {
                            if (!matches(null, value, target)) {
                                nextValues.add(value);
                            }
                        }
                    }
                }
                nextByClass.put(entry.getKey(), nextValues);
            }
        }
        if (baseClass != null) {
            nextByClass.put(baseClass, nextAll);
        }

        ReflectionAccess.put(multiMap, nextAll, "allInstances", "f_13529_");
        ReflectionAccess.put(multiMap, nextByClass, "byClass", "f_13527_");
    }

    private static boolean matches(Object key, Object value, Entity target) {
        if (key != null && (key.equals(target.getId()) || key.equals(target.getUUID()))) {
            return true;
        }
        if (value == target || target.getUUID().equals(value)) {
            return true;
        }
        return value instanceof Entity entity
                && (entity == target || entity.getUUID().equals(target.getUUID()));
    }
}
