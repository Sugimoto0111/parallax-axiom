package dev.srryo.ultimatum.kill;

import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityInLevelCallback;

import java.lang.reflect.Method;

public final class DeepEntityEraser {
    public void erase(ServerLevel level, Entity target) {
        notifyClients(level, target);

        try {
            if (target instanceof LivingEntity living) {
                living.setHealth(0.0F);
            }
        } catch (Throwable ignored) {
        }

        Object callback = ReflectionAccess.get(target, "levelCallback", "f_146801_");
        ReflectionAccess.put(target, Entity.RemovalReason.KILLED, "removalReason", "f_146795_");

        if (callback != null && callback != EntityInLevelCallback.NULL) {
            try {
                Method method = ReflectionAccess.findMethod(callback.getClass(), "onRemove",
                        Entity.RemovalReason.class);
                if (method != null) {
                    method.setAccessible(true);
                    method.invoke(callback, Entity.RemovalReason.KILLED);
                }
            } catch (Throwable error) {
                UltimatumMod.LOGGER.debug("Entity callback removal was rejected for {}", target, error);
            }
        }

        ObjectGraphPurger purger = new ObjectGraphPurger(target);
        purger.purge(ReflectionAccess.get(level, "entityTickList", "f_143243_"));
        purger.purge(ReflectionAccess.get(level, "entityManager", "f_143244_"));
        Object chunkSource = level.getChunkSource();
        purger.purge(ReflectionAccess.get(chunkSource, "chunkMap", "f_8325_", "f_140150_"));

        ReflectionAccess.put(target, EntityInLevelCallback.NULL, "levelCallback", "f_146801_");
        try {
            target.stopRiding();
            target.ejectPassengers();
            target.discard();
        } catch (Throwable ignored) {
        }
    }

    private static void notifyClients(ServerLevel level, Entity target) {
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(target.getId());
        for (ServerPlayer player : level.players()) {
            try {
                player.connection.send(packet);
            } catch (Throwable error) {
                UltimatumMod.LOGGER.debug("Could not send forced removal for {} to {}",
                        target, player.getGameProfile().getName(), error);
            }
        }
    }
}
