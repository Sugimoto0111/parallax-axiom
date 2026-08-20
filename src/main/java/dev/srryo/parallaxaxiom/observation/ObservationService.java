package dev.srryo.parallaxaxiom.observation;

import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.particles.ParticleTypes;

/** Records self-restoration or terminal outcomes into an offhand spyglass. */
public final class ObservationService {
    public static final int REQUIRED_OBSERVATIONS = 50;
    public static final String OBSERVATION_TAG = "ParallaxAxiomObservation";
    private static final String MODE_TAG = "Mode";
    private static final String COUNT_TAG = "Count";
    private static final String TERMINAL_RECORDED_TAG =
            "ParallaxAxiomTerminalObservationRecorded";
    private static final TagKey<EntityType<?>> OBSERVABLE_CONCLUSIONS = TagKey.create(
            Registries.ENTITY_TYPE,
            new ResourceLocation(ParallaxAxiomMod.MOD_ID, "observable_conclusions"));

    private ObservationService() {
    }

    public static void observeBossDeath(LivingEntity target, DamageSource source) {
        if (target.level().isClientSide || !target.getType().is(OBSERVABLE_CONCLUSIONS)
                || target.getPersistentData().getBoolean(TERMINAL_RECORDED_TAG)) {
            return;
        }
        Player observer = findResponsiblePlayer(target, source);
        if (observer != null && record(observer, Mode.TERMINAL)) {
            target.getPersistentData().putBoolean(TERMINAL_RECORDED_TAG, true);
        }
    }

    public static void observeTotemProtection(LivingEntity protectedEntity) {
        if (!protectedEntity.level().isClientSide
                && protectedEntity instanceof Player player) {
            record(player, Mode.ORIGINAL);
        }
    }

    /** Public so GameTest can validate the same state transition used by mixins. */
    public static boolean record(Player player, Mode requestedMode) {
        ItemStack spyglass = player.getOffhandItem();
        if (!spyglass.is(Items.SPYGLASS)) {
            return false;
        }

        CompoundTag observation = spyglass.getOrCreateTagElement(OBSERVATION_TAG);
        Mode existingMode = mode(spyglass);
        if (existingMode != null && existingMode != requestedMode) {
            player.displayClientMessage(Component.translatable(
                    "message.parallax_axiom.observation.locked."
                            + existingMode.serializedName), true);
            return false;
        }
        if (existingMode == null) {
            observation.putString(MODE_TAG, requestedMode.serializedName);
        }

        int count = Math.min(REQUIRED_OBSERVATIONS,
                Math.max(0, observation.getInt(COUNT_TAG)) + 1);
        observation.putInt(COUNT_TAG, count);
        if (count < REQUIRED_OBSERVATIONS) {
            player.displayClientMessage(Component.translatable(
                    "message.parallax_axiom.observation.progress."
                            + requestedMode.serializedName,
                    count, REQUIRED_OBSERVATIONS), true);
            player.level().playSound(null, player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    0.55F, requestedMode == Mode.ORIGINAL ? 0.82F : 1.18F);
            return true;
        }

        ItemStack completed = new ItemStack(requestedMode == Mode.ORIGINAL
                ? ParallaxAxiomMod.ORIGINAL_IMAGE_MIRROR.get()
                : ParallaxAxiomMod.TERMINAL_IMAGE_MIRROR.get());
        player.setItemInHand(InteractionHand.OFF_HAND, completed);
        player.displayClientMessage(Component.translatable(
                "message.parallax_axiom.observation.complete."
                        + requestedMode.serializedName), true);
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS,
                1.0F, requestedMode == Mode.ORIGINAL ? 0.78F : 1.22F);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                    player.getEyeY(), player.getZ(), 36,
                    0.45D, 0.65D, 0.45D, 0.025D);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(),
                    player.getEyeY(), player.getZ(), 42,
                    0.7D, 0.85D, 0.7D, 0.04D);
        }
        return true;
    }

    public static Mode mode(ItemStack stack) {
        CompoundTag root = stack.getTagElement(OBSERVATION_TAG);
        if (root == null || !root.contains(MODE_TAG, Tag.TAG_STRING)) {
            return null;
        }
        return Mode.fromSerializedName(root.getString(MODE_TAG));
    }

    public static int count(ItemStack stack) {
        CompoundTag root = stack.getTagElement(OBSERVATION_TAG);
        return root == null ? 0 : Math.min(REQUIRED_OBSERVATIONS,
                Math.max(0, root.getInt(COUNT_TAG)));
    }

    private static Player findResponsiblePlayer(LivingEntity target,
                                                DamageSource source) {
        Entity causingEntity = source.getEntity();
        if (causingEntity instanceof Player player) {
            return player;
        }
        LivingEntity credit = target.getKillCredit();
        return credit instanceof Player player ? player : null;
    }

    public enum Mode {
        ORIGINAL("original"),
        TERMINAL("terminal");

        private final String serializedName;

        Mode(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

        private static Mode fromSerializedName(String name) {
            for (Mode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            return null;
        }
    }
}
