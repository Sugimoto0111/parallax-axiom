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

/** Owns the mask-contour shader and its reload-safe animation uniforms. */
@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FinalConclusionShaders {
    private static ShaderInstance traceShader;
    private static Uniform timeUniform;
    private static Uniform attackUniform;

    private FinalConclusionShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        ShaderInstance shader = new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(UltimatumMod.MOD_ID, "final_conclusion_trace"),
                DefaultVertexFormat.NEW_ENTITY);
        event.registerShader(shader, FinalConclusionShaders::acceptShader);
    }

    private static void acceptShader(ShaderInstance shader) {
        traceShader = shader;
        timeUniform = shader.getUniform("Time");
        attackUniform = shader.getUniform("Attack");
    }

    static ShaderInstance traceShader() {
        ShaderInstance shader = traceShader;
        return shader != null ? shader : GameRenderer.getRendertypeEntityTranslucentShader();
    }

    static void prepare(float time, float attack) {
        if (traceShader == null) {
            return;
        }
        set(timeUniform, time);
        set(attackUniform, attack);
    }

    private static void set(Uniform uniform, float value) {
        if (uniform != null) {
            uniform.set(value);
        }
    }
}
