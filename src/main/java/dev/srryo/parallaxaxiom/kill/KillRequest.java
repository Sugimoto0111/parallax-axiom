package dev.srryo.parallaxaxiom.kill;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.UUID;

record KillRequest(ResourceKey<Level> dimension, UUID targetUuid, int targetId,
                   UUID attackerUuid, WeakReference<Entity> targetReference) {
}
