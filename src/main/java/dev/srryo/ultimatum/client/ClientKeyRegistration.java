package dev.srryo.ultimatum.client;

import dev.srryo.ultimatum.UltimatumMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ClientKeyRegistration {
    private ClientKeyRegistration() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(ClientKeys.CYCLE_REACH);
        event.register(ClientKeys.CYCLE_FLIGHT);
        event.register(ClientKeys.TOGGLE_STEP_ASSIST);
        event.register(ClientKeys.TOGGLE_INERTIA);
        event.register(ClientKeys.TOGGLE_NIGHT_VISION);
        event.register(ClientKeys.TOGGLE_ITEM_MAGNET);
    }
}
