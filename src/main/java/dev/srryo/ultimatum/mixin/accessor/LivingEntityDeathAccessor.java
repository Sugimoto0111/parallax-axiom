package dev.srryo.ultimatum.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityDeathAccessor {
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> ultimatum$getDataHealthId() {
        throw new AssertionError();
    }

    @Accessor("dead")
    boolean ultimatum$isDeadFlag();

    @Accessor("dead")
    void ultimatum$setDeadFlag(boolean dead);

    @Accessor("deathTime")
    void ultimatum$setDeathTime(int deathTime);

    @Accessor("lastHurtByPlayer")
    void ultimatum$setLastHurtByPlayer(Player player);

    @Accessor("lastHurtByPlayerTime")
    void ultimatum$setLastHurtByPlayerTime(int ticks);

    @Accessor("lastHurtByMob")
    void ultimatum$setLastHurtByMob(LivingEntity attacker);

    @Invoker("dropAllDeathLoot")
    void ultimatum$dropAllDeathLoot(DamageSource source);

    @Invoker("getDeathSound")
    SoundEvent ultimatum$getDeathSound();

    @Invoker("getSoundVolume")
    float ultimatum$getSoundVolume();
}
