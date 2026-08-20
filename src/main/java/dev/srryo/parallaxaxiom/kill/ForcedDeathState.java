package dev.srryo.parallaxaxiom.kill;

import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared marker read by the universal method hooks installed by the embedded agent. */
public final class ForcedDeathState {
    private static final Set<UUID> MARKED = ConcurrentHashMap.newKeySet();

    private ForcedDeathState() {
    }

    public static void mark(Entity entity) {
        MARKED.add(entity.getUUID());
    }

    public static void mark(UUID uuid) {
        MARKED.add(uuid);
    }

    public static boolean contains(UUID uuid) {
        return MARKED.contains(uuid);
    }
}
