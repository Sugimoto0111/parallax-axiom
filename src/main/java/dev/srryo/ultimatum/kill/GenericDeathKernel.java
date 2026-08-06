package dev.srryo.ultimatum.kill;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.mixin.accessor.LivingEntityDeathAccessor;
import dev.srryo.ultimatum.mixin.accessor.SynchedEntityDataAccessor;
import dev.srryo.ultimatum.mixin.accessor.SynchedEntityDataItemAccessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A mod-agnostic death path that writes the vanilla backing state directly. It is only
 * entered after ordinary damage was rejected, and it still gives the target's own die()
 * implementation one chance before reconstructing the minimum death semantics itself.
 */
final class GenericDeathKernel {
    private static final String SEMANTICS_EMITTED_TAG = "ultimatum:generic_death_emitted";

    boolean execute(ServerLevel level, LivingEntity target, @Nullable ServerPlayer attacker) {
        if (target instanceof ServerPlayer) {
            return false;
        }

        DamageSource source = attacker == null
                ? level.damageSources().genericKill()
                : level.damageSources().playerAttack(attacker);
        LivingEntityDeathAccessor access = (LivingEntityDeathAccessor) target;

        forceVanillaHealth(target, 0.0F);
        target.setPose(Pose.DYING);
        if (attacker != null) {
            access.ultimatum$setLastHurtByPlayer(attacker);
            access.ultimatum$setLastHurtByPlayerTime(100);
            access.ultimatum$setLastHurtByMob(attacker);
        }

        // A prior die() may have completed while a custom getHealth()/isAlive() lied about
        // the result. Never emit loot or death events twice in that case.
        if (access.ultimatum$isDeadFlag()) {
            return true;
        }

        try {
            target.die(source);
        } catch (Throwable error) {
            UltimatumMod.LOGGER.debug("Target-specific die() rejected generic death for {}", target, error);
        }
        if (access.ultimatum$isDeadFlag()) {
            return true;
        }

        // The target canceled or replaced die(). Establish the backing state ourselves,
        // then reproduce the observable death contract before the delayed erase fallback.
        access.ultimatum$setDeadFlag(true);
        access.ultimatum$setDeathTime(1);
        target.setPose(Pose.DYING);
        forceVanillaHealth(target, 0.0F);

        if (!target.getPersistentData().getBoolean(SEMANTICS_EMITTED_TAG)) {
            target.getPersistentData().putBoolean(SEMANTICS_EMITTED_TAG, true);
            try {
                MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(target, source));
            } catch (Throwable error) {
                UltimatumMod.LOGGER.debug("Synthetic LivingDeathEvent was rejected for {}", target, error);
            }
            try {
                access.ultimatum$dropAllDeathLoot(source);
            } catch (Throwable error) {
                UltimatumMod.LOGGER.warn("Could not emit generic death loot for {}", target, error);
            }
        }
        try {
            level.broadcastEntityEvent(target, (byte) 3);
        } catch (Throwable error) {
            UltimatumMod.LOGGER.debug("Could not broadcast generic death animation for {}", target, error);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static void forceVanillaHealth(LivingEntity target, float health) {
        EntityDataAccessor<Float> healthId = LivingEntityDeathAccessor.ultimatum$getDataHealthId();
        SynchedEntityData data = target.getEntityData();
        try {
            SynchedEntityData.DataItem<Float> item =
                    ((SynchedEntityDataAccessor) data).ultimatum$getItem(healthId);
            ((SynchedEntityDataItemAccessor<Float>) (Object) item).ultimatum$setValue(health);
            ((SynchedEntityDataItemAccessor<Float>) (Object) item).ultimatum$setDirty(true);
            ((SynchedEntityDataAccessor) data).ultimatum$setDirty(true);
            target.onSyncedDataUpdated(healthId);
        } catch (Throwable error) {
            // Public setter remains a useful compatibility fallback when another transformer
            // changes SynchedEntityData's internal layout.
            data.set(healthId, health, true);
        }
    }
}
