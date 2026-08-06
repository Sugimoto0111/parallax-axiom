package dev.srryo.ultimatum.mixin;

import dev.srryo.ultimatum.UltimatumMod;
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
    private void ultimatum$blockExecutedEntity(Entity entity, CallbackInfo callback) {
        if (UltimatumMod.KILL_SERVICE.blocksReentry(entity)) {
            callback.cancel();
        }
    }
}
