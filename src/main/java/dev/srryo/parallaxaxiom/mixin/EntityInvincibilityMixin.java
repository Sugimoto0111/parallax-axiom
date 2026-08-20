package dev.srryo.parallaxaxiom.mixin;

import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Entity.class, priority = 2000)
public abstract class EntityInvincibilityMixin {
    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockKill(CallbackInfo callback) {
        if (parallaxAxiom$protected()) {
            parallaxAxiom$restore();
            callback.cancel();
        }
    }

    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockDiscard(CallbackInfo callback) {
        if (parallaxAxiom$protected()) {
            parallaxAxiom$restore();
            callback.cancel();
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockRemove(Entity.RemovalReason reason, CallbackInfo callback) {
        if (parallaxAxiom$protected() && parallaxAxiom$harmful(reason)) {
            parallaxAxiom$restore();
            callback.cancel();
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockSetRemoved(Entity.RemovalReason reason, CallbackInfo callback) {
        if (parallaxAxiom$protected() && parallaxAxiom$harmful(reason)) {
            parallaxAxiom$restore();
            callback.cancel();
        }
    }

    @Inject(method = "isRemoved", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$stayPresent(CallbackInfoReturnable<Boolean> callback) {
        Entity self = parallaxAxiom$self();
        if (InvincibilityService.isProtected(self) && parallaxAxiom$harmful(self.getRemovalReason())) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$stayAlive(CallbackInfoReturnable<Boolean> callback) {
        if (parallaxAxiom$protected()) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$doNotSaveExecutedEntity(CallbackInfoReturnable<Boolean> callback) {
        if (ParallaxAxiomMod.KILL_SERVICE.blocksReentry(parallaxAxiom$self())) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "setTicksFrozen", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockFreezing(int ticks, CallbackInfo callback) {
        if (ticks > 0 && parallaxAxiom$protected()) {
            callback.cancel();
        }
    }

    @Inject(method = "setIsInPowderSnow", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockPowderSnow(boolean inPowderSnow, CallbackInfo callback) {
        if (inPowderSnow && parallaxAxiom$protected()) {
            callback.cancel();
        }
    }

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockStuckMultiplier(BlockState state, Vec3 multiplier,
                                                 CallbackInfo callback) {
        if (parallaxAxiom$protected()) {
            callback.cancel();
        }
    }

    private boolean parallaxAxiom$protected() {
        return InvincibilityService.isProtected(parallaxAxiom$self());
    }

    private void parallaxAxiom$restore() {
        if (parallaxAxiom$self() instanceof LivingEntity living) {
            InvincibilityService.restoreNow(living);
        }
    }

    private Entity parallaxAxiom$self() {
        return (Entity) (Object) this;
    }

    private static boolean parallaxAxiom$harmful(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED;
    }
}
