package dev.srryo.ultimatum.network;

import net.minecraft.world.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Client-local tombstones. The set is reset when the client changes worlds. */
public final class ClientExecutionState {
    private static final Set<UUID> ERASED = ConcurrentHashMap.newKeySet();
    private static WeakReference<Object> currentLevel = new WeakReference<>(null);

    private ClientExecutionState() {
    }

    public static synchronized void mark(Object level, UUID uuid) {
        selectLevel(level);
        ERASED.add(uuid);
    }

    public static synchronized boolean blocks(Entity entity) {
        if (entity == null || entity.level() == null) {
            return false;
        }
        selectLevel(entity.level());
        return ERASED.contains(entity.getUUID());
    }

    private static void selectLevel(Object level) {
        if (currentLevel.get() != level) {
            ERASED.clear();
            currentLevel = new WeakReference<>(level);
        }
    }
}
