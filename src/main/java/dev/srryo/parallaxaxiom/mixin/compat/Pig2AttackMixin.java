package dev.srryo.parallaxaxiom.mixin.compat;

import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "kakiku.pig2mod.entity.Pig2", remap = false, priority = 2500)
public abstract class Pig2AttackMixin {
    @Inject(method = "killPlayers", at = @At("HEAD"), cancellable = true, remap = false)
    private void parallaxAxiom$blockKillPlayers(CallbackInfo callback) {
        if (InvincibilityService.hasAnyProtectedPlayer()) {
            callback.cancel();
        }
    }

    @Inject(method = "kickPlayer", at = @At("HEAD"), cancellable = true, remap = false)
    private void parallaxAxiom$blockKickPlayer(ServerPlayer player, CallbackInfo callback) {
        if (InvincibilityService.isProtected(player)) {
            callback.cancel();
        }
    }
}
