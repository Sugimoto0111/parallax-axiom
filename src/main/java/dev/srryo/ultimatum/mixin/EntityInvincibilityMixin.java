package dev.srryo.ultimatum.mixin;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.invincibility.InvincibilityService;
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
    private void ultimatum$blockKill(CallbackInfo callback) {
        if (ultimatum$protected()) {
            ultimatum$restore();
            callback.cancel();
        }
    }

    @Inject(method = "discard", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockDiscard(CallbackInfo callback) {
        if (ultimatum$protected()) {
            ultimatum$restore();
            callback.cancel();
        }
    }

    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockRemove(Entity.RemovalReason reason, CallbackInfo callback) {
        if (ultimatum$protected() && ultimatum$harmful(reason)) {
            ultimatum$restore();
            callback.cancel();
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockSetRemoved(Entity.RemovalReason reason, CallbackInfo callback) {
        if (ultimatum$protected() && ultimatum$harmful(reason)) {
            ultimatum$restore();
            callback.cancel();
        }
    }

    @Inject(method = "isRemoved", at = @At("HEAD"), cancellable = true)
    private void ultimatum$stayPresent(CallbackInfoReturnable<Boolean> callback) {
        Entity self = ultimatum$self();
        if (InvincibilityService.isProtected(self) && ultimatum$harmful(self.getRemovalReason())) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "isAlive", at = @At("HEAD"), cancellable = true)
    private void ultimatum$stayAlive(CallbackInfoReturnable<Boolean> callback) {
        if (ultimatum$protected()) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
    private void ultimatum$doNotSaveExecutedEntity(CallbackInfoReturnable<Boolean> callback) {
        if (UltimatumMod.KILL_SERVICE.blocksReentry(ultimatum$self())) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "setTicksFrozen", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockFreezing(int ticks, CallbackInfo callback) {
        if (ticks > 0 && ultimatum$protected()) {
            callback.cancel();
        }
    }

    @Inject(method = "setIsInPowderSnow", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockPowderSnow(boolean inPowderSnow, CallbackInfo callback) {
        if (inPowderSnow && ultimatum$protected()) {
            callback.cancel();
        }
    }

    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockStuckMultiplier(BlockState state, Vec3 multiplier,
                                                 CallbackInfo callback) {
        if (ultimatum$protected()) {
            callback.cancel();
        }
    }

    private boolean ultimatum$protected() {
        return InvincibilityService.isProtected(ultimatum$self());
    }

    private void ultimatum$restore() {
        if (ultimatum$self() instanceof LivingEntity living) {
            InvincibilityService.restoreNow(living);
        }
    }

    private Entity ultimatum$self() {
        return (Entity) (Object) this;
    }

    private static boolean ultimatum$harmful(Entity.RemovalReason reason) {
        return reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED;
    }
}
