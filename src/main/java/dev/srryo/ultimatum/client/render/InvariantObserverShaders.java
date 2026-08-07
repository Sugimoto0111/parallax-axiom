package dev.srryo.ultimatum.client.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/** Keeps all depth layers on the same spatial rainbow so additive overlap stays coloured. */
@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class InvariantObserverShaders {
    private static ShaderInstance shader;
    private static Uniform timeUniform;

    private InvariantObserverShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        ShaderInstance loaded = new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(UltimatumMod.MOD_ID, "invariant_observer"),
                DefaultVertexFormat.NEW_ENTITY);
        event.registerShader(loaded, InvariantObserverShaders::acceptShader);
    }

    private static void acceptShader(ShaderInstance loaded) {
        shader = loaded;
        timeUniform = loaded.getUniform("Time");
    }

    static ShaderInstance shader() {
        ShaderInstance current = shader;
        return current != null ? current : GameRenderer.getRendertypeEntityTranslucentShader();
    }

    static void prepare(float time) {
        if (timeUniform != null) {
            timeUniform.set(time);
        }
    }
}
