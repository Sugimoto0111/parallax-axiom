package dev.srryo.ultimatum.mixin;

import dev.srryo.ultimatum.invincibility.InvincibilityService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LivingEntity.class, priority = 2000)
public abstract class LivingEntityInvincibilityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockHurt(DamageSource source, float amount,
                                     CallbackInfoReturnable<Boolean> callback) {
        LivingEntity self = ultimatum$self();
        if (InvincibilityService.isProtected(self)) {
            InvincibilityService.restoreNow(self);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockHealthReduction(float health, CallbackInfo callback) {
        LivingEntity self = ultimatum$self();
        if (InvincibilityService.isProtected(self)
                && (!Float.isFinite(health) || health < InvincibilityService.protectedHealth(self))) {
            callback.cancel();
        }
    }

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void ultimatum$stableHealth(CallbackInfoReturnable<Float> callback) {
        LivingEntity self = ultimatum$self();
        if (InvincibilityService.isProtected(self)) {
            callback.setReturnValue(InvincibilityService.protectedHealth(self));
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockDeath(DamageSource source, CallbackInfo callback) {
        LivingEntity self = ultimatum$self();
        if (InvincibilityService.isProtected(self)) {
            InvincibilityService.restoreNow(self);
            callback.cancel();
        }
    }

    @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
    private void ultimatum$notDying(CallbackInfoReturnable<Boolean> callback) {
        if (InvincibilityService.isProtected(ultimatum$self())) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockDeathTick(CallbackInfo callback) {
        LivingEntity self = ultimatum$self();
        if (InvincibilityService.isProtected(self)) {
            InvincibilityService.restoreNow(self);
            callback.cancel();
        }
    }

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockHarmfulEffect(MobEffectInstance effect,
                                               CallbackInfoReturnable<Boolean> callback) {
        LivingEntity self = ultimatum$self();
        if (InvincibilityService.isProtected(self) && InvincibilityService.isHarmful(effect)) {
            callback.setReturnValue(false);
        }
    }

    private LivingEntity ultimatum$self() {
        return (LivingEntity) (Object) this;
    }
}
