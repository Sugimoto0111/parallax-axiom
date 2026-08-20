package dev.srryo.parallaxaxiom.mobility;

import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.ForgeMod;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Fuel-free survival flight owned by the equipped Invariant Observer. */
public final class ArtifactMobilityService {
    public static final String FLIGHT_STAGE_TAG = "ParallaxAxiomFlightStage";
    public static final String INERTIA_DISABLED_TAG = "ParallaxAxiomInertiaDisabled";
    public static final String STEP_ASSIST_TAG = "ParallaxAxiomStepAssist";
    public static final UUID STEP_ASSIST_ID =
            UUID.fromString("d58c9e52-48fb-43a1-bf89-358a3c943518");
    public static final float NORMAL_FLIGHT_SPEED = 0.10F;
    public static final float BOOSTED_FLIGHT_SPEED = 0.30F;
    private static final int DEFAULT_FLIGHT_STAGE = 1;
    private static final float[] FLIGHT_STAGES = {
            0.05F, NORMAL_FLIGHT_SPEED, 0.25F, 0.50F, 1.00F
    };

    private final Map<Player, OriginalAbilities> originals =
            Collections.synchronizedMap(new WeakHashMap<>());

    public void updateNow(Player player) {
        if (!InvincibilityService.hasAbsoluteArtifactEquipped(player)) {
            disableNow(player);
            return;
        }

        Abilities abilities = player.getAbilities();
        originals.computeIfAbsent(player,
                ignored -> new OriginalAbilities(abilities.mayfly, abilities.getFlyingSpeed()));
        float requestedSpeed = requestedFlightSpeed(player);
        boolean changed = !abilities.mayfly
                || Float.compare(abilities.getFlyingSpeed(), requestedSpeed) != 0;
        abilities.mayfly = true;
        abilities.setFlyingSpeed(requestedSpeed);
        if (!player.level().isClientSide) {
            updateStepAssist(player);
        }
        if (changed && !player.level().isClientSide) {
            player.onUpdateAbilities();
        }
    }

    public int flightStage(Player player) {
        return InvincibilityService.findAbsoluteArtifact(player)
                .map(result -> flightStage(result.stack()))
                .orElse(DEFAULT_FLIGHT_STAGE);
    }

    public float baseFlightSpeed(Player player) {
        return FLIGHT_STAGES[flightStage(player)];
    }

    public float requestedFlightSpeed(Player player) {
        float base = baseFlightSpeed(player);
        return player.isSprinting() ? Math.min(base * 3.0F, 1.00F) : base;
    }

    public void cycleFlight(ServerPlayer player) {
        InvincibilityService.findAbsoluteArtifact(player).ifPresent(result -> {
            ItemStack stack = result.stack();
            int next = (flightStage(stack) + 1) % FLIGHT_STAGES.length;
            stack.getOrCreateTag().putInt(FLIGHT_STAGE_TAG, next);
            updateNow(player);
            int creativePercent = Math.round(FLIGHT_STAGES[next] / 0.05F * 100.0F);
            player.displayClientMessage(Component.translatable(
                    "message.parallax_axiom.flight.changed",
                    Component.translatable("message.parallax_axiom.flight.stage." + next),
                    creativePercent).withStyle(ChatFormatting.AQUA), true);
        });
    }

    public void toggleInertia(ServerPlayer player) {
        InvincibilityService.findAbsoluteArtifact(player).ifPresent(result -> {
            ItemStack stack = result.stack();
            boolean enabled = !isInertiaDisabled(stack);
            stack.getOrCreateTag().putBoolean(INERTIA_DISABLED_TAG, enabled);
            if (enabled) {
                clearMotion(player);
            }
            player.displayClientMessage(Component.translatable(
                    enabled
                            ? "message.parallax_axiom.inertia.enabled"
                            : "message.parallax_axiom.inertia.disabled")
                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        });
    }

    public void toggleStepAssist(ServerPlayer player) {
        InvincibilityService.findAbsoluteArtifact(player).ifPresent(result -> {
            ItemStack stack = result.stack();
            boolean enabled = !isStepAssistEnabled(stack);
            stack.getOrCreateTag().putBoolean(STEP_ASSIST_TAG, enabled);
            updateStepAssist(player);
            player.displayClientMessage(Component.translatable(
                    enabled
                            ? "message.parallax_axiom.step_assist.enabled"
                            : "message.parallax_axiom.step_assist.disabled")
                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        });
    }

    public static boolean isInertiaDisabled(Player player) {
        return InvincibilityService.findAbsoluteArtifact(player)
                .map(result -> isInertiaDisabled(result.stack()))
                .orElse(false);
    }

    public static void toggleInertiaLocally(Player player) {
        InvincibilityService.findAbsoluteArtifact(player).ifPresent(result -> {
            ItemStack stack = result.stack();
            boolean enabled = !isInertiaDisabled(stack);
            stack.getOrCreateTag().putBoolean(INERTIA_DISABLED_TAG, enabled);
            if (enabled) {
                player.setDeltaMovement(Vec3.ZERO);
            }
        });
    }

    public static void applyClientInertiaControl(Player player,
                                                  boolean horizontalInput,
                                                  boolean verticalInput) {
        if (!player.getAbilities().flying || !isInertiaDisabled(player)) {
            return;
        }
        Vec3 movement = player.getDeltaMovement();
        double x = horizontalInput ? movement.x : 0.0D;
        double y = verticalInput ? movement.y : 0.0D;
        double z = horizontalInput ? movement.z : 0.0D;
        if (x != movement.x || y != movement.y || z != movement.z) {
            player.setDeltaMovement(x, y, z);
        }
    }

    public void disableNow(Player player) {
        removeStepAssist(player);
        OriginalAbilities original = originals.remove(player);
        if (original == null) {
            return;
        }

        Abilities abilities = player.getAbilities();
        boolean changed = abilities.mayfly != original.mayFly()
                || Float.compare(abilities.getFlyingSpeed(), original.flyingSpeed()) != 0;
        abilities.mayfly = original.mayFly();
        abilities.setFlyingSpeed(original.flyingSpeed());
        if (!original.mayFly()) {
            changed |= abilities.flying;
            abilities.flying = false;
        }
        if (changed && !player.level().isClientSide) {
            player.onUpdateAbilities();
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            updateNow(event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        disableNow(event.getEntity());
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        originals.remove(event.getOriginal());
    }

    private static int flightStage(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(FLIGHT_STAGE_TAG)) {
            return DEFAULT_FLIGHT_STAGE;
        }
        int stored = stack.getTag().getInt(FLIGHT_STAGE_TAG);
        return Math.max(0, Math.min(FLIGHT_STAGES.length - 1, stored));
    }

    private static boolean isInertiaDisabled(ItemStack stack) {
        return !stack.hasTag()
                || !stack.getTag().contains(INERTIA_DISABLED_TAG)
                || stack.getTag().getBoolean(INERTIA_DISABLED_TAG);
    }

    private static boolean isStepAssistEnabled(ItemStack stack) {
        return !stack.hasTag()
                || !stack.getTag().contains(STEP_ASSIST_TAG)
                || stack.getTag().getBoolean(STEP_ASSIST_TAG);
    }

    private static void clearMotion(Player player) {
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;
        player.fallDistance = 0.0F;
    }

    private static void updateStepAssist(Player player) {
        AttributeInstance stepHeight = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (stepHeight == null) {
            return;
        }
        if (!InvincibilityService.findAbsoluteArtifact(player)
                .map(result -> isStepAssistEnabled(result.stack())).orElse(false)) {
            stepHeight.removeModifier(STEP_ASSIST_ID);
            return;
        }
        AttributeModifier current = stepHeight.getModifier(STEP_ASSIST_ID);
        if (current == null || Double.compare(current.getAmount(), 1.0D) != 0) {
            stepHeight.removeModifier(STEP_ASSIST_ID);
            stepHeight.addTransientModifier(new AttributeModifier(
                    STEP_ASSIST_ID, "Invariant Observer step assist", 1.0D,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    private static void removeStepAssist(Player player) {
        AttributeInstance stepHeight = player.getAttribute(ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (stepHeight != null) {
            stepHeight.removeModifier(STEP_ASSIST_ID);
        }
    }

    private record OriginalAbilities(boolean mayFly, float flyingSpeed) {
    }
}
