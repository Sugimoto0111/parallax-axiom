package dev.srryo.ultimatum.mixin;

import dev.srryo.ultimatum.network.ClientExecutionState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.TransientEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refuses client-side recreation packets for an entity already erased in this world. */
@Mixin(value = TransientEntitySectionManager.class, priority = 2000)
public abstract class TransientEntitySectionManagerTombstoneMixin<T extends EntityAccess> {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockClientReentry(T access, CallbackInfo callback) {
        if (access instanceof Entity entity && ClientExecutionState.blocks(entity)) {
            callback.cancel();
        }
    }
}
