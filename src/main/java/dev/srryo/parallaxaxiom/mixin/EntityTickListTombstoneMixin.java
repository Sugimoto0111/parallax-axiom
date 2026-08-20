package dev.srryo.parallaxaxiom.mixin;

import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTickList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blocks paths that try to restore only the ticking index and skip the entity manager. */
@Mixin(value = EntityTickList.class, priority = 2000)
public abstract class EntityTickListTombstoneMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void parallaxAxiom$blockExecutedEntity(Entity entity, CallbackInfo callback) {
        if (ParallaxAxiomMod.KILL_SERVICE.blocksReentry(entity)) {
            callback.cancel();
        }
    }
}
