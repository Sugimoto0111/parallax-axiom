package dev.srryo.ultimatum.mixin;

import dev.srryo.ultimatum.observation.ObservationService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla-owned observation hooks that remain available when Forge's bus is replaced. */
@Mixin(value = LivingEntity.class, priority = 2000)
public abstract class LivingEntityObservationMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void ultimatum$observeTerminalOutcome(DamageSource source,
                                                   CallbackInfo callback) {
        ObservationService.observeBossDeath((LivingEntity) (Object) this, source);
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
    private void ultimatum$observeOriginalImage(DamageSource source,
            CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) {
            ObservationService.observeTotemProtection((LivingEntity) (Object) this);
        }
    }
}
