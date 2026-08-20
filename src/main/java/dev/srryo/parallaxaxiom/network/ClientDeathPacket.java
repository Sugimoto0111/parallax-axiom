package dev.srryo.parallaxaxiom.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ClientDeathPacket(int entityId, UUID entityUuid) {
    static void encode(ClientDeathPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId());
        buffer.writeUUID(message.entityUuid());
    }

    static ClientDeathPacket decode(FriendlyByteBuf buffer) {
        return new ClientDeathPacket(buffer.readVarInt(), buffer.readUUID());
    }

    static void handle(ClientDeathPacket message,
                       Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> ClientExecutionHandler.startDeath(message));
        }
        context.setPacketHandled(true);
    }
}
