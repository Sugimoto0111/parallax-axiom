package dev.srryo.parallaxaxiom.network;

import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ArtifactControlPacket(Action action) {
    static void encode(ArtifactControlPacket message, FriendlyByteBuf buffer) {
        buffer.writeEnum(message.action());
    }

    static ArtifactControlPacket decode(FriendlyByteBuf buffer) {
        return new ArtifactControlPacket(buffer.readEnum(Action.class));
    }

    static void handle(ArtifactControlPacket message,
                       Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> handle(sender, message.action()));
        }
        context.setPacketHandled(true);
    }

    private static void handle(ServerPlayer player, Action action) {
        switch (action) {
            case CYCLE_FLIGHT -> ParallaxAxiomMod.ARTIFACT_MOBILITY_SERVICE.cycleFlight(player);
            case TOGGLE_STEP_ASSIST ->
                    ParallaxAxiomMod.ARTIFACT_MOBILITY_SERVICE.toggleStepAssist(player);
            case TOGGLE_INERTIA -> ParallaxAxiomMod.ARTIFACT_MOBILITY_SERVICE.toggleInertia(player);
            case TOGGLE_NIGHT_VISION ->
                    ParallaxAxiomMod.ARTIFACT_UTILITY_SERVICE.toggleNightVision(player);
            case TOGGLE_ITEM_MAGNET ->
                    ParallaxAxiomMod.ARTIFACT_UTILITY_SERVICE.toggleItemMagnet(player);
        }
    }

    public enum Action {
        CYCLE_FLIGHT,
        TOGGLE_STEP_ASSIST,
        TOGGLE_INERTIA,
        TOGGLE_NIGHT_VISION,
        TOGGLE_ITEM_MAGNET
    }
}
