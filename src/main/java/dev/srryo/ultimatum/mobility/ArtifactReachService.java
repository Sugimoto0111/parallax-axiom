package dev.srryo.ultimatum.mobility;

import dev.srryo.ultimatum.invincibility.InvincibilityService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

/** Persistent, staged block/entity reach for the Absolute Artifact. */
public final class ArtifactReachService {
    public static final String REACH_STAGE_TAG = "UltimatumReachStage";
    private static final double[] REACH_STAGES = {5.0D, 8.0D, 16.0D, 32.0D, 64.0D};
    private static final UUID BLOCK_REACH_ID =
            UUID.fromString("bdb23715-0f21-4e70-a5fc-76fe4ccfb924");
    private static final UUID ENTITY_REACH_ID =
            UUID.fromString("44e8cead-64ca-4d51-aa49-7b1b738ac7f7");

    public int stage(Player player) {
        return InvincibilityService.findAbsoluteArtifact(player)
                .map(result -> stage(result.stack()))
                .orElse(0);
    }

    public double reach(Player player) {
        return REACH_STAGES[stage(player)];
    }

    public void cycle(ServerPlayer player) {
        InvincibilityService.findAbsoluteArtifact(player).ifPresent(result -> {
            ItemStack stack = result.stack();
            int next = (stage(stack) + 1) % REACH_STAGES.length;
            stack.getOrCreateTag().putInt(REACH_STAGE_TAG, next);
            updateNow(player);
            Component value = next == 0
                    ? Component.translatable("message.ultimatum.reach.standard")
                    : Component.literal(Integer.toString((int) REACH_STAGES[next]));
            player.displayClientMessage(Component.translatable(
                    "message.ultimatum.reach.changed", value)
                    .withStyle(ChatFormatting.GOLD), true);
        });
    }

    public void updateNow(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        if (!InvincibilityService.hasAbsoluteArtifactEquipped(player)) {
            disableNow(player);
            return;
        }
        double target = reach(player);
        apply(player.getAttribute(ForgeMod.BLOCK_REACH.get()), BLOCK_REACH_ID,
                "Absolute Artifact block reach", target);
        apply(player.getAttribute(ForgeMod.ENTITY_REACH.get()), ENTITY_REACH_ID,
                "Absolute Artifact entity reach", target);
    }

    public void disableNow(Player player) {
        remove(player.getAttribute(ForgeMod.BLOCK_REACH.get()), BLOCK_REACH_ID);
        remove(player.getAttribute(ForgeMod.ENTITY_REACH.get()), ENTITY_REACH_ID);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            updateNow(event.player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        disableNow(event.getEntity());
    }

    private static int stage(ItemStack stack) {
        int stored = stack.getOrCreateTag().getInt(REACH_STAGE_TAG);
        return Math.max(0, Math.min(REACH_STAGES.length - 1, stored));
    }

    private static void apply(AttributeInstance attribute, UUID id, String name, double target) {
        if (attribute == null) {
            return;
        }
        double addition = Math.max(0.0D, target - attribute.getBaseValue());
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && Double.compare(current.getAmount(), addition) == 0) {
            return;
        }
        attribute.removeModifier(id);
        if (addition > 0.0D) {
            attribute.addTransientModifier(new AttributeModifier(id, name, addition,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    private static void remove(AttributeInstance attribute, UUID id) {
        if (attribute != null) {
            attribute.removeModifier(id);
        }
    }
}
