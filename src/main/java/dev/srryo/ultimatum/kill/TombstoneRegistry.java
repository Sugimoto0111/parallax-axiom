package dev.srryo.ultimatum.kill;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

final class TombstoneRegistry {
    private final CopyOnWriteArrayList<Tombstone> entries = new CopyOnWriteArrayList<>();

    void add(ServerLevel level, Entity entity, long expiresAt) {
        entries.add(new Tombstone(level.dimension(), entity.getUUID(), expiresAt));
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
        return false;
    }

    void tick(long now) {
        entries.removeIf(entry -> entry.expiresAt < now);
    }

    private record Tombstone(ResourceKey<Level> dimension, UUID uuid, long expiresAt) {
    }
}
