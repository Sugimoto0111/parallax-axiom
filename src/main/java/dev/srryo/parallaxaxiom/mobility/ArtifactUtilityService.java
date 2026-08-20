package dev.srryo.parallaxaxiom.mobility;

import dev.srryo.parallaxaxiom.invincibility.InvincibilityService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Toggleable utility powers supplied by the Invariant Observer. */
public final class ArtifactUtilityService {
    public static final String NIGHT_VISION_TAG = "ParallaxAxiomNightVision";
    public static final String ITEM_MAGNET_TAG = "ParallaxAxiomItemMagnet";
    public static final double ITEM_MAGNET_RADIUS = 64.0D;
    private static final int NIGHT_VISION_DURATION = 400;

    private final Map<Player, Boolean> appliedNightVision =
            Collections.synchronizedMap(new WeakHashMap<>());

    public void toggleNightVision(ServerPlayer player) {
        toggle(player, NIGHT_VISION_TAG, "message.parallax_axiom.night_vision");
        updateNightVision(player);
    }

    public void toggleItemMagnet(ServerPlayer player) {
        toggle(player, ITEM_MAGNET_TAG, "message.parallax_axiom.magnet");
        if (isEnabled(player, ITEM_MAGNET_TAG)) {
            attractItemsNow(player);
        }
    }

    public void updateNow(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        if (!InvincibilityService.hasAbsoluteArtifactEquipped(player)) {
            disableNow(player);
            return;
        }
        updateNightVision(player);
        if (player.tickCount % 2 == 0 && isEnabled(player, ITEM_MAGNET_TAG)) {
            attractItemsNow(player);
        }
    }

    public void disableNow(Player player) {
        if (appliedNightVision.remove(player) != null) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    public void attractItemsNow(Player player) {
        if (player.level().isClientSide
                || !InvincibilityService.hasAbsoluteArtifactEquipped(player)
                || !isEnabled(player, ITEM_MAGNET_TAG)) {
            return;
        }
        AABB area = player.getBoundingBox().inflate(ITEM_MAGNET_RADIUS);
        Vec3 destination = player.position().add(0.0D, 0.75D, 0.0D);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, area,
                candidate -> !candidate.isRemoved() && canAttract(player, candidate))) {
            Vec3 offset = destination.subtract(item.position());
            double distance = offset.length();
            if (distance < 2.25D) {
                item.playerTouch(player);
                continue;
            }
            if (distance < 1.0E-6D) {
                continue;
            }
            double pullSpeed = Math.min(3.0D, 0.20D + distance * 0.06D);
            Vec3 pull = offset.scale(pullSpeed / distance);
            item.setDeltaMovement(item.getDeltaMovement().scale(0.25D).add(pull));
            item.hasImpulse = true;
        }
    }

    public static boolean isEnabled(Player player, String tagName) {
        return InvincibilityService.findAbsoluteArtifact(player)
                .map(result -> result.stack().hasTag()
                        && result.stack().getTag().getBoolean(tagName))
                .orElse(false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            updateNow(event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        disableNow(event.getEntity());
    }

    private void updateNightVision(Player player) {
        boolean enabled = isEnabled(player, NIGHT_VISION_TAG);
        if (!enabled) {
            if (appliedNightVision.remove(player) != null) {
                player.removeEffect(MobEffects.NIGHT_VISION);
            }
            return;
        }

        boolean ours = appliedNightVision.containsKey(player);
        if (!ours && player.hasEffect(MobEffects.NIGHT_VISION)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,
                NIGHT_VISION_DURATION, 0, true, false, false));
        appliedNightVision.put(player, Boolean.TRUE);
    }

    private static void toggle(ServerPlayer player, String tagName, String messagePrefix) {
        InvincibilityService.findAbsoluteArtifact(player).ifPresent(result -> {
            ItemStack stack = result.stack();
            boolean enabled = !stack.getOrCreateTag().getBoolean(tagName);
            stack.getOrCreateTag().putBoolean(tagName, enabled);
            player.displayClientMessage(Component.translatable(
                    messagePrefix + (enabled ? ".enabled" : ".disabled"))
                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        });
    }

    private static boolean canAttract(Player player, ItemEntity item) {
        Entity owner = item.getOwner();
        return owner == null || owner == player;
    }
}
