package dev.srryo.parallaxaxiom.mixin.compat;

import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "flashfur.omnimobs.entities.flashfur.powers.FlashfurInvulnerabilityDetection",
        remap = false, priority = 3000)
public abstract class OmniFlashfurAttackMixin {
    @Inject(method = "onAttackEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void parallaxAxiom$blockInvulnerabilityPunish(LivingEntity entity, boolean canTakeDamage,
                                                       float previousHealth, CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            InvincibilityService.restoreNow(entity);
            callback.cancel();
        }
    }
}
