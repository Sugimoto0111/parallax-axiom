package dev.srryo.ultimatum.mixin;

import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents a tombstoned entity from being inserted directly into the server's
 * persistent entity indexes, including paths that skip Forge EntityJoinLevelEvent.
 */
@Mixin(value = PersistentEntitySectionManager.class, priority = 2000)
public abstract class PersistentEntitySectionManagerMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void ultimatum$blockTombstoneReentry(EntityAccess access, boolean existing,
                                                 CallbackInfoReturnable<Boolean> callback) {
        if (access instanceof Entity entity && UltimatumMod.KILL_SERVICE.blocksReentry(entity)) {
            callback.setReturnValue(false);
        }
    }

    // Forge-added method: its name is not part of Mojang's obfuscation mappings.
    @Inject(method = "addEntityWithoutEvent", at = @At("HEAD"), cancellable = true,
            remap = false)
    private void ultimatum$blockTombstoneReentryWithoutEvent(
            EntityAccess access, boolean existing, CallbackInfoReturnable<Boolean> callback) {
        if (access instanceof Entity entity && UltimatumMod.KILL_SERVICE.blocksReentry(entity)) {
            callback.setReturnValue(false);
        }
    }
}
