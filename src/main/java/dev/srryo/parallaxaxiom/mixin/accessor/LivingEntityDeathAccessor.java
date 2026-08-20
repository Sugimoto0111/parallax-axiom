package dev.srryo.parallaxaxiom.mixin.accessor;

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
    static EntityDataAccessor<Float> parallaxAxiom$getDataHealthId() {
        throw new AssertionError();
    }

    @Accessor("dead")
    boolean parallaxAxiom$isDeadFlag();

    @Accessor("dead")
    void parallaxAxiom$setDeadFlag(boolean dead);

    @Accessor("deathTime")
    void parallaxAxiom$setDeathTime(int deathTime);

    @Accessor("lastHurtByPlayer")
    void parallaxAxiom$setLastHurtByPlayer(Player player);

    @Accessor("lastHurtByPlayerTime")
    void parallaxAxiom$setLastHurtByPlayerTime(int ticks);

    @Accessor("lastHurtByMob")
    void parallaxAxiom$setLastHurtByMob(LivingEntity attacker);

    @Invoker("dropAllDeathLoot")
    void parallaxAxiom$dropAllDeathLoot(DamageSource source);

    @Invoker("getDeathSound")
    SoundEvent parallaxAxiom$getDeathSound();

    @Invoker("getSoundVolume")
    float parallaxAxiom$getSoundVolume();
}
