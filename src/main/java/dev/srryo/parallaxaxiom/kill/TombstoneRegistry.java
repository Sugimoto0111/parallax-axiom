package dev.srryo.parallaxaxiom.kill;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class TombstoneRegistry {
    private final CopyOnWriteArrayList<Tombstone> entries = new CopyOnWriteArrayList<>();

    void add(ServerLevel level, Entity entity, long expiresAt) {
        entries.add(new Tombstone(level.dimension(), entity.getUUID(), expiresAt));
    }

    void addPermanent(ServerLevel level, Entity entity) {
        UUID uuid = entity.getUUID();
        entries.addIfAbsent(new Tombstone(level.dimension(), uuid, Long.MAX_VALUE));
        ExecutionTombstoneData.get(level).add(uuid);
    }

    boolean blocks(ServerLevel level, Entity entity, long now) {
        for (Tombstone entry : entries) {
            if (entry.expiresAt < now || !entry.dimension.equals(level.dimension())) {
                continue;
            }
            if (entry.uuid.equals(entity.getUUID())) {
                return true;
            }
        }
        return ExecutionTombstoneData.get(level).contains(entity.getUUID());
    }

    List<Entity> findReentered(ServerLevel level, long now) {
        List<Entity> result = new ArrayList<>();
        ExecutionTombstoneData permanent = ExecutionTombstoneData.get(level);
        for (Entity entity : level.getAllEntities()) {
            if (permanent.contains(entity.getUUID()) || blocksInMemory(level, entity, now)) {
                result.add(entity);
            }
        }
        return result;
    }

    private boolean blocksInMemory(ServerLevel level, Entity entity, long now) {
        for (Tombstone entry : entries) {
            if (entry.expiresAt >= now && entry.dimension.equals(level.dimension())
                    && entry.uuid.equals(entity.getUUID())) {
                return true;
            }
        }
        return false;
    }

    void tick(long now) {
        entries.removeIf(entry -> entry.expiresAt < now);
    }

    private record Tombstone(ResourceKey<Level> dimension, UUID uuid, long expiresAt) {
    }
}
