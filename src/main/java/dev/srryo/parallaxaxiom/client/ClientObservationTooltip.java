package dev.srryo.parallaxaxiom.client;

import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import dev.srryo.parallaxaxiom.observation.ObservationService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ParallaxAxiomMod.MOD_ID, value = Dist.CLIENT)
public final class ClientObservationTooltip {
    private ClientObservationTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.SPYGLASS)) {
            return;
        }
        ObservationService.Mode mode = ObservationService.mode(stack);
        if (mode == null) {
            return;
        }
        event.getToolTip().add(Component.translatable(
                        "tooltip.parallax_axiom.observation." + mode.serializedName(),
                        ObservationService.count(stack),
                        ObservationService.REQUIRED_OBSERVATIONS)
                .withStyle(mode == ObservationService.Mode.ORIGINAL
                        ? ChatFormatting.AQUA : ChatFormatting.LIGHT_PURPLE));
    }
}
