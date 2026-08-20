package dev.srryo.parallaxaxiom.kill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public interface KillAdapter {
    boolean supports(Entity target);

    default boolean allowStandardDeath(Entity target) {
        return true;
    }

    /**
     * Hides a confirmed death from clients immediately while the server keeps running
     * the target's real death/drop lifecycle. Useful for custom bosses whose corpse
     * remains visually alive during a delayed death timer.
     */
    default boolean concealConfirmedDeath(Entity target) {
        return false;
    }

    /**
     * Returns true when even probing the public hurt path is dangerous (for example, it
     * launches a retaliation before rejecting the damage). The service still establishes
     * generic death semantics first, then runs the adapter and erases in the same tick.
     */
    default boolean requiresImmediateIsolation(Entity target) {
        return false;
    }

    default void onStandardDeath(ServerLevel level, Entity target, ServerPlayer attacker,
                                 KillService service) throws Exception {
    }

    void execute(ServerLevel level, Entity target, ServerPlayer attacker, KillService service) throws Exception;

    String name();
}
