package dev.srryo.ultimatum.client.render;

import dev.srryo.ultimatum.UltimatumMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/** Registers the Curios renderer only on physical clients. */
@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientArtifactRendererRegistration {
    private ClientArtifactRendererRegistration() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> CuriosRendererRegistry.register(
                UltimatumMod.ABSOLUTE_ARTIFACT.get(), ObserverArrayRenderer::new));
    }
}
