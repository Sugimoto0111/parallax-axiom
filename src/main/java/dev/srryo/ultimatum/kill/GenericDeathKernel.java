package dev.srryo.ultimatum.kill;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.mixin.accessor.LivingEntityDeathAccessor;
import dev.srryo.ultimatum.mixin.accessor.SynchedEntityDataAccessor;
import dev.srryo.ultimatum.mixin.accessor.SynchedEntityDataItemAccessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

/**
 * NoSugar-style forced death. It deliberately does not call target hurt/setHealth/die:
 * those virtual methods are exactly where protected mobs reject or replace death.
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

        ForcedDeathState.mark(target);
        ExternalErasedStateBridge.mark(target);
        forceVanillaHealth(target, 0.0F);
        target.setPose(Pose.DYING);
        try {
            target.getCombatTracker().recordDamage(source, Float.POSITIVE_INFINITY);
        } catch (Throwable ignored) {
        }
        if (attacker != null) {
            access.ultimatum$setLastHurtByPlayer(attacker);
            access.ultimatum$setLastHurtByPlayerTime(100);
            access.ultimatum$setLastHurtByMob(attacker);
        }

        access.ultimatum$setDeadFlag(true);
        access.ultimatum$setDeathTime(1);
        target.setPose(Pose.DYING);
        forceVanillaHealth(target, 0.0F);

        if (!target.getPersistentData().getBoolean(SEMANTICS_EMITTED_TAG)) {
            target.getPersistentData().putBoolean(SEMANTICS_EMITTED_TAG, true);
            try {
                access.ultimatum$dropAllDeathLoot(source);
            } catch (Throwable error) {
                UltimatumMod.LOGGER.warn("Could not emit generic death loot for {}", target, error);
            }
            try {
                MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(target, source));
            } catch (Throwable error) {
                UltimatumMod.LOGGER.debug("Synthetic LivingDeathEvent was rejected for {}", target, error);
            }
            playDeathSounds(target, access);
        }
        return true;
    }

    private static void playDeathSounds(LivingEntity target,
                                        LivingEntityDeathAccessor access) {
        try {
            SoundEvent sound = access.ultimatum$getDeathSound();
            if (sound != null) {
                target.playSound(sound, access.ultimatum$getSoundVolume(),
                        target.getVoicePitch());
            }
        } catch (Throwable error) {
            UltimatumMod.LOGGER.debug("Could not play forced death sound for {}", target, error);
        }
        try {
            target.playSound(SoundEvents.PLAYER_ATTACK_STRONG,
                    access.ultimatum$getSoundVolume(), target.getVoicePitch());
        } catch (Throwable ignored) {
        }
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
