package dev.srryo.ultimatum.kill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public interface KillAdapter {
    boolean supports(Entity target);

    default boolean allowStandardDeath(Entity target) {
        return true;
    }

    default void onStandardDeath(ServerLevel level, Entity target, ServerPlayer attacker,
                                 KillService service) throws Exception {
    }

    void execute(ServerLevel level, Entity target, ServerPlayer attacker, KillService service) throws Exception;

    String name();
}
