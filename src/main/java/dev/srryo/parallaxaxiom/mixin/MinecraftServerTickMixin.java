package dev.srryo.parallaxaxiom.mixin;

import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * A vanilla-owned heartbeat for the kill pipeline. Some adversarial mods replace
 * MinecraftForge.EVENT_BUS, so Forge TickEvent cannot be our only scheduler.
 */
@Mixin(value = MinecraftServer.class, priority = 2000)
public abstract class MinecraftServerTickMixin {
    @Inject(method = "tickServer", at = @At("TAIL"))
    private void parallaxAxiom$runExecutionHeartbeat(BooleanSupplier hasTimeLeft,
                                                  CallbackInfo callback) {
        ParallaxAxiomMod.KILL_SERVICE.onVanillaServerTick((MinecraftServer) (Object) this);
    }
}
