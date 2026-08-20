package dev.srryo.parallaxaxiom.mixin.compat;

import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "flashfur.omnimobs.util.EntityRemovalUtil", remap = false, priority = 2500)
public abstract class OmniRemovalUtilMixin {
    @Inject(method = "deleteEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private static void parallaxAxiom$blockDeleteEntity(Entity entity, Level level, boolean onRemoved,
                                                     CallbackInfo callback) {
        if (InvincibilityService.isProtected(entity)) {
            callback.cancel();
        }
    }
}
