package dev.srryo.ultimatum.mixin;

import dev.srryo.ultimatum.network.ClientExecutionState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NoSugar-style client death clock for entities whose own alive methods lie. */
@Mixin(value = LivingEntity.class, priority = 2000)
public abstract class LivingEntityExecutionAnimationMixin {
    @Shadow public int deathTime;
    @Shadow private boolean dead;

    @Inject(method = "baseTick", at = @At("HEAD"))
    private void ultimatum$advanceForcedDeathAnimation(CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide || !ClientExecutionState.blocks(self)) {
            return;
        }
        dead = true;
        self.setPose(Pose.DYING);
        if (deathTime <= 20) {
            deathTime++;
        }
    }

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void ultimatum$holdCorpseForClientSchedule(CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.level().isClientSide && ClientExecutionState.blocks(self)) {
            callback.cancel();
        }
    }
}
