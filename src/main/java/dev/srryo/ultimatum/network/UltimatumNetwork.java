package dev.srryo.ultimatum.network;

import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class UltimatumNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(UltimatumMod.MOD_ID, "execution"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);
    private static boolean registered;

    private UltimatumNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(0, ClientErasePacket.class,
                ClientErasePacket::encode,
                ClientErasePacket::decode,
                ClientErasePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, ClientDeathPacket.class,
                ClientDeathPacket::encode,
                ClientDeathPacket::decode,
                ClientDeathPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, ReachCyclePacket.class,
                ReachCyclePacket::encode,
                ReachCyclePacket::decode,
                ReachCyclePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, ArtifactControlPacket.class,
                ArtifactControlPacket::encode,
                ArtifactControlPacket::decode,
                ArtifactControlPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void eraseFor(ServerPlayer player, Entity target) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientErasePacket(target.getId(), target.getUUID()));
    }

    public static void animateDeathFor(ServerPlayer player, Entity target) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ClientDeathPacket(target.getId(), target.getUUID()));
    }

    public static void cycleReach() {
        CHANNEL.sendToServer(new ReachCyclePacket());
    }

    public static void controlArtifact(ArtifactControlPacket.Action action) {
        CHANNEL.sendToServer(new ArtifactControlPacket(action));
    }
}
