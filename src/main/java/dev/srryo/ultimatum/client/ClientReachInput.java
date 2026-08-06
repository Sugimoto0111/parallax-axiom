package dev.srryo.ultimatum.client;

import dev.srryo.ultimatum.UltimatumMod;
import dev.srryo.ultimatum.mobility.ArtifactMobilityService;
import dev.srryo.ultimatum.network.ArtifactControlPacket;
import dev.srryo.ultimatum.network.UltimatumNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID, value = Dist.CLIENT)
public final class ClientReachInput {
    private ClientReachInput() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            while (ClientKeys.CYCLE_REACH.consumeClick()) {
                UltimatumNetwork.cycleReach();
            }
            while (ClientKeys.CYCLE_FLIGHT.consumeClick()) {
                UltimatumNetwork.controlArtifact(
                        ArtifactControlPacket.Action.CYCLE_FLIGHT);
            }
            while (ClientKeys.TOGGLE_STEP_ASSIST.consumeClick()) {
                UltimatumNetwork.controlArtifact(
                        ArtifactControlPacket.Action.TOGGLE_STEP_ASSIST);
            }
            while (ClientKeys.TOGGLE_INERTIA.consumeClick()) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    ArtifactMobilityService.toggleInertiaLocally(player);
                }
                UltimatumNetwork.controlArtifact(
                        ArtifactControlPacket.Action.TOGGLE_INERTIA);
            }
            while (ClientKeys.TOGGLE_NIGHT_VISION.consumeClick()) {
                UltimatumNetwork.controlArtifact(
                        ArtifactControlPacket.Action.TOGGLE_NIGHT_VISION);
            }
            while (ClientKeys.TOGGLE_ITEM_MAGNET.consumeClick()) {
                UltimatumNetwork.controlArtifact(
                        ArtifactControlPacket.Action.TOGGLE_ITEM_MAGNET);
            }

            applyInertiaControl();
        }
    }

    private static void applyInertiaControl() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        boolean horizontalInput = minecraft.options.keyUp.isDown()
                || minecraft.options.keyDown.isDown()
                || minecraft.options.keyLeft.isDown()
                || minecraft.options.keyRight.isDown();
        boolean verticalInput = minecraft.options.keyJump.isDown()
                || minecraft.options.keyShift.isDown();
        ArtifactMobilityService.applyClientInertiaControl(
                player, horizontalInput, verticalInput);
    }
}
