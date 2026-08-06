package dev.srryo.ultimatum.mixin.compat;

import dev.srryo.ultimatum.invincibility.InvincibilityService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "flashfur.omnimobs.util.EntityUtil", remap = false, priority = 2500)
public abstract class OmniEntityUtilMixin {
    @Inject(method = "forceHurt", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ultimatum$blockForcedHurt(LivingEntity attacker, LivingEntity entity,
                                                   @Coerce Object damageSource,
                                                   CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            InvincibilityService.restoreNow(entity);
            callback.cancel();
        }
    }

    @Inject(method = "forceSetHealth", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ultimatum$blockForcedHealth(LivingEntity entity, float value,
                                                     CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            InvincibilityService.restoreNow(entity);
            callback.cancel();
        }
    }

    @Inject(method = "forceSetRemoved", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ultimatum$blockForcedSetRemoved(Entity entity, Entity.RemovalReason reason,
                                                         CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            callback.cancel();
        }
    }

    @Inject(method = {"forceRemove", "forceRemoveNoLeaveLevelCalls"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private static void ultimatum$blockForcedRemove(Entity entity, Entity.RemovalReason reason,
                                                     CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            callback.cancel();
        }
    }

    @Inject(method = "forceRemoveNoPacket", at = @At("HEAD"), cancellable = true, remap = false)
    private static void ultimatum$blockForcedRemoveNoPacket(Entity entity, Entity.RemovalReason reason,
                                                             boolean leaveLevelCalls,
                                                             CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            callback.cancel();
        }
    }
}
