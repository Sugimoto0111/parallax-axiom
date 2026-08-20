package dev.srryo.parallaxaxiom.kill;

import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import dev.srryo.parallaxaxiom.network.ParallaxAxiomNetwork;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityInLevelCallback;

import java.lang.reflect.Method;

public final class DeepEntityEraser {
    public void erase(ServerLevel level, Entity target) {
        concealConfirmedDeath(level, target);

        try {
            if (target instanceof LivingEntity living) {
                living.setHealth(0.0F);
            }
        } catch (Throwable ignored) {
        }

        Object callback = ReflectionAccess.get(target, "levelCallback", "f_146801_");
        ReflectionAccess.put(target, Entity.RemovalReason.KILLED, "removalReason", "f_146795_");

        // First perform deterministic, atomic replacements of every vanilla world index.
        // The graph purge below remains as a broader fallback for Forge and hostile-mod
        // wrappers not represented by the normal field layout.
        DeterministicWorldIndexEraser.erase(level, target);

        if (callback != null && callback != EntityInLevelCallback.NULL) {
            try {
                Method method = ReflectionAccess.findMethod(callback.getClass(), "onRemove",
                        Entity.RemovalReason.class);
                if (method != null) {
                    method.setAccessible(true);
                    method.invoke(callback, Entity.RemovalReason.KILLED);
                }
            } catch (Throwable error) {
                ParallaxAxiomMod.LOGGER.debug("Entity callback removal was rejected for {}", target, error);
            }
        }

        ObjectGraphPurger purger = new ObjectGraphPurger(target);
        purger.purge(ReflectionAccess.get(level, "entityTickList", "f_143243_"));
        purger.purge(ReflectionAccess.get(level, "entityManager", "f_143244_"));
        Object chunkSource = level.getChunkSource();
        purger.purge(ReflectionAccess.get(chunkSource, "chunkMap", "f_8325_", "f_140150_"));

        ReflectionAccess.put(target, EntityInLevelCallback.NULL, "levelCallback", "f_146801_");
        ReflectionAccess.put(target, false, "isAddedToWorld");
        ReflectionAccess.put(target, false, "canUpdate");
        try {
            target.stopRiding();
            target.ejectPassengers();
            target.invalidateCaps();
            target.discard();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Removes only the client representation. The server entity remains alive long enough
     * to complete its already-confirmed real death, loot and mod callbacks.
     */
    public void concealConfirmedDeath(ServerLevel level, Entity target) {
        notifyClients(level, target);

        // Unknown mods frequently keep their boss bar in a private controller. Walk the
        // target-owned graph and clear those events before detaching the entity itself.
        new GenericBossEventPurger(level).purge(target);
    }

    private static void notifyClients(ServerLevel level, Entity target) {
        ClientboundRemoveEntitiesPacket packet = new ClientboundRemoveEntitiesPacket(target.getId());
        for (ServerPlayer player : level.players()) {
            try {
                player.connection.send(packet);
                ParallaxAxiomNetwork.eraseFor(player, target);
            } catch (Throwable error) {
                ParallaxAxiomMod.LOGGER.debug("Could not send forced removal for {} to {}",
                        target, player.getGameProfile().getName(), error);
            }
        }
    }
}
