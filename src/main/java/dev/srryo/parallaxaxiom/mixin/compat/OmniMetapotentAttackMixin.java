package dev.srryo.parallaxaxiom.mixin.compat;

import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "flashfur.omnimobs.entities.metapotent_flashfur.MetapotentFlashfur",
        remap = false, priority = 3000)
public abstract class OmniMetapotentAttackMixin {
    @Inject(method = "instantKill", at = @At("HEAD"), cancellable = true, remap = false)
    private void parallaxAxiom$blockInstantKill(LivingEntity entity, float knockback,
                                            CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            InvincibilityService.restoreNow(entity);
            callback.cancel();
        }
    }
}
