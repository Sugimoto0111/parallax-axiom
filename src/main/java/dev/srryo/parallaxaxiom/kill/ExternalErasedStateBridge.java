package dev.srryo.parallaxaxiom.kill;

import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Shares the erased marker when NoSugar is installed. This avoids attaching two agents
 * to ModuleClassLoader while both mods are being compared in the same test instance.
 */
public final class ExternalErasedStateBridge {
    private ExternalErasedStateBridge() {
    }

    public static void mark(Entity entity) {
        invoke(entity, "setErased", new Class<?>[]{boolean.class}, true);
        invoke(entity, "markErased", new Class<?>[]{UUID.class}, entity.getUUID());
    }

    private static void invoke(Object owner, String name, Class<?>[] parameters,
                               Object argument) {
        try {
            Method method = ReflectionAccess.findMethod(owner.getClass(), name, parameters);
            if (method != null) {
                method.setAccessible(true);
                method.invoke(owner, argument);
            }
        } catch (Throwable ignored) {
            // NoSugar is optional; absence of its injected interface is expected.
        }
    }
}
