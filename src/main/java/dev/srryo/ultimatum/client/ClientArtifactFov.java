package dev.srryo.ultimatum.client;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.invincibility.InvincibilityService;
import dev.srryo.ultimatum.item.AbsoluteArtifactItem;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps Artifact movement power from inflating the camera FOV. */
@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID, value = Dist.CLIENT)
public final class ClientArtifactFov {
    private ClientArtifactFov() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (!InvincibilityService.hasAbsoluteArtifactEquipped(player)) {
            return;
        }
        AttributeInstance movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        float walkingSpeed = player.getAbilities().getWalkingSpeed();
        if (movement == null || walkingSpeed == 0.0F) {
            return;
        }

        double actualSpeed = movement.getValue();
        double speedWithoutArtifact = valueWithout(
                movement, AbsoluteArtifactItem.MOVEMENT_SPEED_ID);
        double actualFactor = (actualSpeed / walkingSpeed + 1.0D) / 2.0D;
        double correctedFactor = (speedWithoutArtifact / walkingSpeed + 1.0D) / 2.0D;
        if (!Double.isFinite(actualFactor) || !Double.isFinite(correctedFactor)
                || Math.abs(actualFactor) < 1.0E-8D) {
            return;
        }

        float correctedFov = (float) (event.getNewFovModifier()
                * correctedFactor / actualFactor);
        if (Float.isFinite(correctedFov)) {
            event.setNewFovModifier(correctedFov);
        }
    }

    private static double valueWithout(AttributeInstance attribute,
                                       java.util.UUID excludedId) {
        double afterAddition = attribute.getBaseValue();
        for (AttributeModifier modifier : attribute.getModifiers(
                AttributeModifier.Operation.ADDITION)) {
            if (!modifier.getId().equals(excludedId)) {
                afterAddition += modifier.getAmount();
            }
        }

        double afterBaseMultipliers = afterAddition;
        for (AttributeModifier modifier : attribute.getModifiers(
                AttributeModifier.Operation.MULTIPLY_BASE)) {
            if (!modifier.getId().equals(excludedId)) {
                afterBaseMultipliers += afterAddition * modifier.getAmount();
            }
        }

        double result = afterBaseMultipliers;
        for (AttributeModifier modifier : attribute.getModifiers(
                AttributeModifier.Operation.MULTIPLY_TOTAL)) {
            if (!modifier.getId().equals(excludedId)) {
                result *= 1.0D + modifier.getAmount();
            }
        }
        return result;
    }
}
