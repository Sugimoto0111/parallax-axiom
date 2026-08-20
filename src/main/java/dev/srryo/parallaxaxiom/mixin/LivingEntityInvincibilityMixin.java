package dev.srryo.parallaxaxiom.mixin;

import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
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
    private void parallaxAxiom$blockHurt(DamageSource source, float amount,
                                     CallbackInfoReturnable<Boolean> callback) {
        LivingEntity self = parallaxAxiom$self();
        if (InvincibilityService.isProtected(self)) {
            InvincibilityService.restoreNow(self);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockHealthReduction(float health, CallbackInfo callback) {
        LivingEntity self = parallaxAxiom$self();
        if (InvincibilityService.isProtected(self)
                && (!Float.isFinite(health) || health < InvincibilityService.protectedHealth(self))) {
            callback.cancel();
        }
    }

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$stableHealth(CallbackInfoReturnable<Float> callback) {
        LivingEntity self = parallaxAxiom$self();
        if (InvincibilityService.isProtected(self)) {
            callback.setReturnValue(InvincibilityService.protectedHealth(self));
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockDeath(DamageSource source, CallbackInfo callback) {
        LivingEntity self = parallaxAxiom$self();
        if (InvincibilityService.isProtected(self)) {
            InvincibilityService.restoreNow(self);
            callback.cancel();
        }
    }

    @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$notDying(CallbackInfoReturnable<Boolean> callback) {
        if (InvincibilityService.isProtected(parallaxAxiom$self())) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockDeathTick(CallbackInfo callback) {
        LivingEntity self = parallaxAxiom$self();
        if (InvincibilityService.isProtected(self)) {
            InvincibilityService.restoreNow(self);
            callback.cancel();
        }
    }

    @Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockHarmfulEffect(MobEffectInstance effect,
                                               CallbackInfoReturnable<Boolean> callback) {
        LivingEntity self = parallaxAxiom$self();
        if (InvincibilityService.isProtected(self) && InvincibilityService.isHarmful(effect)) {
            callback.setReturnValue(false);
        }
    }

    private LivingEntity parallaxAxiom$self() {
        return (LivingEntity) (Object) this;
    }
}
