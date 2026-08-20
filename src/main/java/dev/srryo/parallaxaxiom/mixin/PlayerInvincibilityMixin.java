package dev.srryo.parallaxaxiom.mixin;

import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Player.class, priority = 2000)
public abstract class PlayerInvincibilityMixin {
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockPlayerHurt(DamageSource source, float amount,
                                           CallbackInfoReturnable<Boolean> callback) {
        Player self = (Player) (Object) this;
        if (InvincibilityService.isProtected(self)) {
            InvincibilityService.restoreNow(self);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$absoluteInvulnerability(DamageSource source,
                                                    CallbackInfoReturnable<Boolean> callback) {
        if (InvincibilityService.isProtected((Player) (Object) this)) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void parallaxAxiom$releaseRestraintsAfterTick(CallbackInfo callback) {
        Player self = (Player) (Object) this;
        if (InvincibilityService.isProtected(self)) {
            InvincibilityService.releaseVanillaRestraints(self);
        }
    }
}
