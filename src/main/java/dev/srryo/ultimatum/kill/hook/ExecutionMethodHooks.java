package dev.srryo.ultimatum.kill.hook;

import dev.srryo.ultimatum.kill.ForcedDeathState;
import dev.srryo.ultimatum.network.ClientExecutionState;
import net.minecraft.world.entity.Entity;

/** Called from bytecode hooks injected into every overriding entity method. */
public final class ExecutionMethodHooks implements ExecutionMethodHook {
    public static final ExecutionMethodHook INSTANCE = new ExecutionMethodHooks();

    private ExecutionMethodHooks() {
    }

    @Override
    public float getHealth(float original, Object entity) {
        return executing(entity) ? 0.0F : original;
    }

    @Override
    public boolean isAlive(boolean original, Object entity) {
        return executing(entity) ? false : original;
    }

    @Override
    public boolean isDeadOrDying(boolean original, Object entity) {
        return executing(entity) ? true : original;
    }

    private static boolean executing(Object value) {
        if (!(value instanceof Entity entity)) {
            return false;
        }
        if (ForcedDeathState.contains(entity.getUUID())) {
            return true;
        }
        return entity.level() != null && entity.level().isClientSide
                && ClientExecutionState.blocks(entity);
    }
}
