package dev.srryo.parallaxaxiom.mixin.compat;

import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "io.github.kosianodangoo.trialmonolith.common.helper.EntityHelper",
        remap = false, priority = 2500)
public abstract class TrialEntityHelperMixin {
    @Inject(method = {"onSoulDeath", "onSoulRemove"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void parallaxAxiom$blockSoulErasure(Entity entity, CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            if (entity instanceof LivingEntity living) {
                InvincibilityService.restoreNow(living);
            }
            callback.cancel();
        }
    }

    @Inject(method = {"setSoulDamage", "setSoulDamageForce", "addSoulDamage", "addSoulDamageForce"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void parallaxAxiom$blockSoulDamage(Entity entity, float value, CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            callback.cancel();
        }
    }
}
