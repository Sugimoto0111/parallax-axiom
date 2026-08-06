package dev.srryo.ultimatum.kill.adapter;

import dev.srryo.ultimatum.kill.KillAdapter;
import dev.srryo.ultimatum.kill.KillService;
import dev.srryo.ultimatum.kill.TrustedKernel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class Pig2Adapter implements KillAdapter {
    private static final String PIG2_CLASS = "kakiku.pig2mod.entity.Pig2";

    @Override
    public boolean supports(Entity target) {
        return target.getClass().getName().equals(PIG2_CLASS);
    }

    @Override
    public boolean allowStandardDeath(Entity target) {
        // Pig2 treats normal death attempts as a shutdown trigger. It is already
        // known to reject that path, so enter the isolated kernel without firing it.
        return false;
    }

    @Override
    public void execute(ServerLevel level, Entity target, ServerPlayer attacker, KillService service) {
        if (!TrustedKernel.executePig(target, level)) {
            // This fallback remains useful when Pig2's launch plugin is absent or disabled.
            service.markAndErase(level, target, 400);
            return;
        }
        service.schedulePigReset(level.getServer(), 4, target.getClass());
    }

    @Override
    public String name() {
        return "Pig2";
    }
}
