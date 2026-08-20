package dev.srryo.ultimatum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public record ClientErasePacket(int entityId, UUID entityUuid) {
    static void encode(ClientErasePacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.entityId());
        buffer.writeUUID(message.entityUuid());
    }

    static ClientErasePacket decode(FriendlyByteBuf buffer) {
        return new ClientErasePacket(buffer.readVarInt(), buffer.readUUID());
    }

    static void handle(ClientErasePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            context.enqueueWork(() -> ClientExecutionHandler.handle(message));
        }
        context.setPacketHandled(true);
    }
}
