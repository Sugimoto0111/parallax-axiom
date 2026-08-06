package dev.srryo.ultimatum.network;

import dev.srryo.ultimatum.kill.DeterministicWorldIndexEraser;
import dev.srryo.ultimatum.kill.ExternalErasedStateBridge;
import dev.srryo.ultimatum.kill.ForcedDeathState;
import dev.srryo.ultimatum.kill.ReflectionAccess;
import dev.srryo.ultimatum.mixin.accessor.LivingEntityDeathAccessor;
import dev.srryo.ultimatum.mixin.accessor.SynchedEntityDataAccessor;
import dev.srryo.ultimatum.mixin.accessor.SynchedEntityDataItemAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/** Loaded only on the physical client by ClientErasePacket. */
final class ClientExecutionHandler {
    private ClientExecutionHandler() {
    }

    static void startDeath(ClientDeathPacket message) {
        ClientLevel level = Minecraft.getInstance().level;
        Entity target = find(level, message.entityId(), message.entityUuid());
        if (level == null || !(target instanceof LivingEntity living)) {
            return;
        }

        ClientExecutionState.mark(level, message.entityUuid());
        ForcedDeathState.mark(message.entityUuid());
        ExternalErasedStateBridge.mark(living);
        living.setPose(Pose.DYING);
        LivingEntityDeathAccessor access = (LivingEntityDeathAccessor) living;
        access.ultimatum$setDeadFlag(true);
        access.ultimatum$setDeathTime(1);
        forceHealthZero(living);
    }

    static void handle(ClientErasePacket message) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        ClientExecutionState.mark(level, message.entityUuid());
        ForcedDeathState.mark(message.entityUuid());
        Entity target = find(level, message.entityId(), message.entityUuid());
        if (target == null) {
            return;
        }

        ExternalErasedStateBridge.mark(target);
        target.setPose(Pose.DYING);
        try {
            target.onClientRemoval();
        } catch (Throwable ignored) {
        }
        try {
            level.removeEntity(target.getId(), Entity.RemovalReason.KILLED);
        } catch (Throwable ignored) {
        }
        try {
            target.setRemoved(Entity.RemovalReason.KILLED);
        } catch (Throwable ignored) {
        }
        DeterministicWorldIndexEraser.eraseClient(level, target);
        ReflectionAccess.put(target, null, "levelCallback", "f_146801_");
    }

    private static Entity find(ClientLevel level, int id, java.util.UUID uuid) {
        if (level == null) {
            return null;
        }
        Entity target = level.getEntity(id);
        if (target != null && target.getUUID().equals(uuid)) {
            return target;
        }
        for (Entity candidate : level.entitiesForRendering()) {
            if (candidate.getUUID().equals(uuid)) {
                return candidate;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static void forceHealthZero(LivingEntity target) {
        EntityDataAccessor<Float> health = LivingEntityDeathAccessor.ultimatum$getDataHealthId();
        SynchedEntityData data = target.getEntityData();
        try {
            SynchedEntityData.DataItem<Float> item =
                    ((SynchedEntityDataAccessor) data).ultimatum$getItem(health);
            ((SynchedEntityDataItemAccessor<Float>) (Object) item).ultimatum$setValue(0.0F);
            ((SynchedEntityDataItemAccessor<Float>) (Object) item).ultimatum$setDirty(true);
            ((SynchedEntityDataAccessor) data).ultimatum$setDirty(true);
        } catch (Throwable ignored) {
            data.set(health, 0.0F, true);
        }
    }
}
