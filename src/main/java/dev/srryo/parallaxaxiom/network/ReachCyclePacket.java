package dev.srryo.parallaxaxiom.network;

import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ReachCyclePacket() {
    static void encode(ReachCyclePacket message, FriendlyByteBuf buffer) {
    }

    static ReachCyclePacket decode(FriendlyByteBuf buffer) {
        return new ReachCyclePacket();
    }

    static void handle(ReachCyclePacket message,
                       Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> ParallaxAxiomMod.ARTIFACT_REACH_SERVICE.cycle(sender));
        }
        context.setPacketHandled(true);
    }
}
