package dev.srryo.ultimatum.mixin;

import dev.srryo.ultimatum.UltimatumMod;
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
    private void ultimatum$runExecutionHeartbeat(BooleanSupplier hasTimeLeft,
                                                  CallbackInfo callback) {
        UltimatumMod.KILL_SERVICE.onVanillaServerTick((MinecraftServer) (Object) this);
        UltimatumMod.ACQUISITION_RITUAL_SERVICE.onVanillaServerTick(
                (MinecraftServer) (Object) this);
    }
}
